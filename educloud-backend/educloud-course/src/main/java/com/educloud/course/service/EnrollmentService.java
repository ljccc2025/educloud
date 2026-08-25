package com.educloud.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.common.api.PageResponse;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.course.dto.response.CourseStudentResponse;
import com.educloud.course.dto.response.EnrollmentResponse;
import com.educloud.course.dto.response.MyCourseResponse;
import com.educloud.course.entity.CourseEnrollmentEntity;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseVersionEntity;
import com.educloud.course.exception.CourseErrorCode;
import com.educloud.course.mapper.CourseEnrollmentMapper;
import com.educloud.course.mapper.CourseMapper;
import com.educloud.course.mapper.CourseMyCourseRow;
import com.educloud.course.mapper.CourseStudentRow;
import com.educloud.course.mapper.CourseVersionMapper;
import com.educloud.course.messaging.CourseEventPublisher;
import com.educloud.course.observability.AuditWriter;
import com.educloud.course.observability.CourseMetrics;
import com.educloud.course.support.TeacherAccessGuard;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 选课服务（M05 任务 13）：免费选课（幂等）/我的课程/教师学生列表。
 *
 * <p>依据：规格 §6/§7/§9 与任务 13 ——
 * <ul>
 *   <li>选课：锁根（selectByIdForUpdate）→ 仅 PUBLISHED（其余生命周期 → 409
 *       COURSE_OFFLINE_OR_ARCHIVED）→ 取 published version 校验免费（付费 → 409
 *       COURSE_NOT_FREE）→ 已存在 enrollment 幂等返回现状（不重复计数/不发事件）→
 *       插入 ACTIVE/FREE + course.enrollment_count 乐观锁递增（UPDATE ... WHERE
 *       id=? AND version=?，0 行 → 409 VERSION_CONFLICT）+ outbox EnrollmentCreated，
 *       全部同一事务；</li>
 *   <li>并发兜底：uk(course_id,student_id) 冲突（DuplicateKeyException）→ 用当前读
 *       （selectByCourseAndStudentForUpdate，SELECT ... FOR UPDATE）重查返回现状——
 *       REPEATABLE READ 一致读快照看不到并发提交行，普通 selectOne 会 500（锁根已
 *       在入口序列化同一课程，此为最后防线）；</li>
 *   <li>我的课程：enrollment JOIN course JOIN published version 分页（ACTIVE、
 *       enrolled_at 倒序），封面按页一次 File 批量 grant（subject=USER 当前学生：
 *       已选课学生访问自己的课程封面，不签匿名公开 URL）；</li>
 *   <li>学生列表：TeacherAccessGuard 归属校验（无归属 → COURSE_ACCESS_DENIED 403）+
 *       enrollment JOIN course 分页（ACTIVE、enrolled_at 倒序）；displayName 恒
 *       null（M05 无 user Profile 客户端，学生展示名解析留给后续接入）。</li>
 * </ul>
 * M05 无撤销触发路径（EnrollmentRevoked 事件与 REVOKED 状态预留 M07 退款接入），
 * 故已存在 enrollment 一律原样返回现状；恢复（REVOKED→ACTIVE）语义归 M07。</p>
 */
@Service
public class EnrollmentService {

    public static final String SOURCE_FREE = "FREE";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String LIFECYCLE_PUBLISHED = "PUBLISHED";

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final CourseMapper courseMapper;
    private final CourseVersionMapper versionMapper;
    private final CourseEnrollmentMapper enrollmentMapper;
    private final TeacherAccessGuard teacherAccessGuard;
    private final CourseEventPublisher eventPublisher;
    private final FileClient fileClient;
    private final CourseMetrics courseMetrics;
    private final AuditWriter auditWriter;

    public EnrollmentService(
            CourseMapper courseMapper,
            CourseVersionMapper versionMapper,
            CourseEnrollmentMapper enrollmentMapper,
            TeacherAccessGuard teacherAccessGuard,
            CourseEventPublisher eventPublisher,
            FileClient fileClient,
            CourseMetrics courseMetrics,
            AuditWriter auditWriter) {
        this.courseMapper = Objects.requireNonNull(courseMapper, "courseMapper");
        this.versionMapper = Objects.requireNonNull(versionMapper, "versionMapper");
        this.enrollmentMapper = Objects.requireNonNull(enrollmentMapper, "enrollmentMapper");
        this.teacherAccessGuard = Objects.requireNonNull(teacherAccessGuard, "teacherAccessGuard");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.fileClient = Objects.requireNonNull(fileClient, "fileClient");
        this.courseMetrics = Objects.requireNonNull(courseMetrics, "courseMetrics");
        this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter");
    }

    /**
     * 免费选课（POST /courses/{id}/enrollments，course:enroll，幂等）。
     *
     * <p>锁根 → 校验 PUBLISHED + published version 免费 → 已存在返回现状；不存在则
     * 插入 ACTIVE/FREE enrollment + enrollment_count 乐观锁递增 + outbox
     * EnrollmentCreated（同一事务）。并发重复请求由根行锁序列化，uk 兜底捕获
     * DuplicateKeyException 重查返回现状。</p>
     */
    @Transactional
    public EnrollmentResponse enroll(Long courseId, Long studentId, Set<String> roles) {
        CourseEntity course = courseMapper.selectByIdForUpdate(courseId);
        if (course == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "Course not found: " + courseId);
        }
        if (!LIFECYCLE_PUBLISHED.equals(course.getLifecycleStatus())) {
            throw new BusinessException(CourseErrorCode.COURSE_OFFLINE_OR_ARCHIVED,
                    "Course is not available for enrollment: " + courseId);
        }
        CourseVersionEntity version = course.getPublishedVersionId() == null
                ? null
                : versionMapper.selectById(course.getPublishedVersionId());
        if (version == null) {
            throw new BusinessException(CourseErrorCode.COURSE_OFFLINE_OR_ARCHIVED,
                    "Course has no published version to enroll: " + courseId);
        }
        if (version.getPrice() == null || version.getPrice().compareTo(ZERO) != 0) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FREE,
                    "Course is not free: " + courseId);
        }

        CourseEnrollmentEntity existing = findEnrollment(courseId, studentId);
        if (existing != null) {
            // 幂等：已存在（M05 可达状态仅 ACTIVE）返回现状，不重复计数、不发事件。
            return toResponse(existing);
        }
        return insertAndPublish(course, studentId, roles);
    }

    /**
     * 付费订单履约选课（M07 任务 10）：幂等开通课程。
     */
    @Transactional
    public CourseEnrollmentEntity enrollPaidCourse(Long courseId, Long studentId, Long orderId) {
        CourseEnrollmentEntity existing = enrollmentMapper.selectByCourseAndStudentForUpdate(courseId, studentId);
        if (existing != null) {
            if ("REVOKED".equals(existing.getStatus())) {
                existing.setStatus(STATUS_ACTIVE);
                existing.setSource("ORDER");
                existing.setSourceOrderId(orderId);
                existing.setRevokeReason(null);
                enrollmentMapper.updateById(existing);
            }
            return existing;
        }

        CourseEntity course = courseMapper.selectByIdForUpdate(courseId);
        if (course == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        CourseEnrollmentEntity enrollment = new CourseEnrollmentEntity();
        enrollment.setCourseId(course.getId());
        enrollment.setStudentId(studentId);
        enrollment.setSource("ORDER");
        enrollment.setSourceOrderId(orderId);
        enrollment.setStatus(STATUS_ACTIVE);
        enrollment.setEnrolledAt(now);
        enrollment.setVersion(0L);

        try {
            enrollmentMapper.insert(enrollment);
        } catch (DuplicateKeyException duplicate) {
            return enrollmentMapper.selectByCourseAndStudentForUpdate(course.getId(), studentId);
        }

        courseMapper.incrementEnrollmentCount(course.getId(), course.getVersion());
        eventPublisher.enrollmentCreated(
                enrollment.getId(),
                course.getId(),
                studentId,
                "ORDER",
                enrollment.getVersion(),
                now);
        courseMetrics.recordEnrollmentCreated();
        return enrollment;
    }

    /** 插入新 enrollment + 计数递增 + EnrollmentCreated；uk 冲突 → 重查返回现状。 */
    private EnrollmentResponse insertAndPublish(CourseEntity course, Long studentId, Set<String> roles) {
        LocalDateTime now = LocalDateTime.now();
        CourseEnrollmentEntity enrollment = new CourseEnrollmentEntity();
        enrollment.setCourseId(course.getId());
        enrollment.setStudentId(studentId);
        enrollment.setSource(SOURCE_FREE);
        enrollment.setStatus(STATUS_ACTIVE);
        enrollment.setEnrolledAt(now);
        // 与 course_enrollment.version DEFAULT 0 对齐：insert 后立即可携带明确版本发事件。
        enrollment.setVersion(0L);
        try {
            if (enrollmentMapper.insert(enrollment) != 1) {
                throw new IllegalStateException(
                        "enrollment insert failed for course " + course.getId() + " student " + studentId);
            }
        } catch (DuplicateKeyException duplicate) {
            // 审查修复：重查必须用当前读（SELECT ... FOR UPDATE）——MySQL REPEATABLE READ
            // 下本事务一致读快照看不到并发事务已提交的 uk 行，普通 selectOne 会返回 null
            // 导致 500；锁读（当前读）直接读最新已提交数据，才能命中并发插入的行。
            CourseEnrollmentEntity existing = enrollmentMapper.selectByCourseAndStudentForUpdate(
                    course.getId(), studentId);
            if (existing == null) {
                throw new IllegalStateException(
                        "enrollment insert raced and current read found no row for course "
                                + course.getId() + " student " + studentId,
                        duplicate);
            }
            return toResponse(existing);
        }

        int incremented = courseMapper.incrementEnrollmentCount(course.getId(), course.getVersion());
        if (incremented == 0) {
            throw new BusinessException(CommonErrorCode.VERSION_CONFLICT,
                    "Course root changed concurrently: " + course.getId());
        }
        eventPublisher.enrollmentCreated(
                enrollment.getId(),
                course.getId(),
                studentId,
                SOURCE_FREE,
                enrollment.getVersion(),
                now);
        courseMetrics.recordEnrollmentCreated();
        auditWriter.write("ENROLLMENT_CREATED", "enrollment", String.valueOf(enrollment.getId()),
                studentId, roles, "SUCCESS", null);
        return toResponse(enrollment);
    }

    /**
     * 我的课程（GET /me/enrollments）：分页返回当前学生 ACTIVE 选课 + 课程信息；
     * 封面按页一次 File 批量 grant（subject=USER 学生本人）。学习进度归 Content
     * 服务（M06 接入），本接口不下发进度。
     */
    public PageResponse<MyCourseResponse> myCourses(Long studentId, Integer page, Integer pageSize) {
        int pageNum = normalizePage(page);
        int sizeNum = normalizeSize(pageSize);
        Page<CourseMyCourseRow> request = new Page<>(pageNum, sizeNum);
        com.baomidou.mybatisplus.core.metadata.IPage<CourseMyCourseRow> result =
                enrollmentMapper.selectMyCoursesPage(request, studentId);
        List<CourseMyCourseRow> rows = result.getRecords();

        Map<Long, String> coverUrls = coverUrls(rows, studentId);
        List<MyCourseResponse> items = rows.stream()
                .map(row -> new MyCourseResponse(
                        String.valueOf(row.getCourseId()),
                        row.getTitle(),
                        row.getCoverFileId() == null ? null : coverUrls.get(row.getCoverFileId()),
                        row.getStatus(),
                        row.getEnrolledAt()))
                .toList();
        return PageResponse.of(items, pageNum, sizeNum, result.getTotal());
    }

    /** 本页封面 fileId→courseId（owner）映射；空/无封面 → 空 Map，不触发 File 调用。 */
    private Map<Long, String> coverUrls(List<CourseMyCourseRow> rows, Long studentId) {
        Map<Long, Long> courseIdByFileId = new HashMap<>();
        for (CourseMyCourseRow row : rows) {
            if (row.getCoverFileId() != null) {
                courseIdByFileId.put(row.getCoverFileId(), row.getCourseId());
            }
        }
        if (courseIdByFileId.isEmpty()) {
            return Map.of();
        }
        // subject=USER（当前学生）：已选课学生可看自己的课程封面，不签匿名公开 URL。
        return fileClient.grantCatalogUrls(courseIdByFileId, studentId);
    }

    /**
     * 教师学生列表（GET /courses/{id}/students，course:student:read + 归属）。
     * 归属校验硬规则（规格 §9）：无 course_teacher 行 → COURSE_ACCESS_DENIED 403
     * （学生/非归属教师一律 403，不泄露课程存在性）；通过后按 enrolled_at 倒序分页。
     */
    public PageResponse<CourseStudentResponse> listStudents(
            Long courseId, Long teacherId, Integer page, Integer pageSize) {
        teacherAccessGuard.requireAccess(courseId, teacherId);
        int pageNum = normalizePage(page);
        int sizeNum = normalizeSize(pageSize);
        Page<CourseStudentRow> request = new Page<>(pageNum, sizeNum);
        com.baomidou.mybatisplus.core.metadata.IPage<CourseStudentRow> result =
                enrollmentMapper.selectStudentPage(request, courseId);
        List<CourseStudentRow> rows = result.getRecords();
        List<CourseStudentResponse> items = rows.stream()
                .map(row -> new CourseStudentResponse(
                        String.valueOf(row.getStudentId()),
                        null,
                        row.getEnrolledAt()))
                .toList();
        return PageResponse.of(items, pageNum, sizeNum, result.getTotal());
    }

    @Transactional
    public void revokeCourseEnrollmentByOrder(Long orderId, String reason) {
        if (orderId == null) {
            return;
        }
        List<CourseEnrollmentEntity> enrollments = enrollmentMapper.selectList(
                new LambdaQueryWrapper<CourseEnrollmentEntity>()
                        .eq(CourseEnrollmentEntity::getSourceOrderId, orderId)
                        .eq(CourseEnrollmentEntity::getStatus, "ACTIVE"));
        for (CourseEnrollmentEntity enrollment : enrollments) {
            enrollment.setStatus("REVOKED");
            enrollment.setRevokeReason(reason != null ? reason : "PAYMENT_REFUNDED");
            enrollmentMapper.updateById(enrollment);
        }
    }

    private CourseEnrollmentEntity findEnrollment(Long courseId, Long studentId) {
        return enrollmentMapper.selectOne(new LambdaQueryWrapper<CourseEnrollmentEntity>()
                .eq(CourseEnrollmentEntity::getCourseId, courseId)
                .eq(CourseEnrollmentEntity::getStudentId, studentId));
    }

    private static EnrollmentResponse toResponse(CourseEnrollmentEntity enrollment) {
        return new EnrollmentResponse(
                String.valueOf(enrollment.getId()),
                String.valueOf(enrollment.getCourseId()),
                String.valueOf(enrollment.getStudentId()),
                enrollment.getSource(),
                enrollment.getStatus(),
                enrollment.getEnrolledAt());
    }

    private static int normalizePage(Integer page) {
        return page == null ? DEFAULT_PAGE : Math.max(page, 1);
    }

    private static int normalizeSize(Integer size) {
        return size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE);
    }
}
