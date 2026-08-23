package com.educloud.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.course.dto.request.ReviewUpsertRequest;
import com.educloud.course.dto.response.CourseReviewResponse;
import com.educloud.course.entity.CourseEnrollmentEntity;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseReviewEntity;
import com.educloud.course.exception.CourseErrorCode;
import com.educloud.course.mapper.CourseEnrollmentMapper;
import com.educloud.course.mapper.CourseMapper;
import com.educloud.course.mapper.CourseReviewMapper;
import com.educloud.course.mapper.CourseReviewSummaryRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * 课程评价服务（M05 任务 14）：学生 upsert 评价 + 管理端隐藏 + 评分汇总重算。
 *
 * <p>依据：规格 §6/§7 ——
 * <ul>
 *   <li>upsert（POST /courses/{id}/reviews）：锁课程根（selectByIdForUpdate，串行化
 *       同课程评价写与重算）→ 校验 ACTIVE 选课（未选课/REVOKED → 403 NOT_ENROLLED）→
 *       INSERT ... ON DUPLICATE KEY UPDATE（uk(course_id, student_id)）→ 同事务按
 *       VISIBLE 评价重算 course.rating_avg/rating_count（HIDDEN 不计入）→ 重查返回
 *       真实行（响应 id/createdAt 以库行为准）；</li>
 *   <li>隐藏（DELETE /course-reviews/{id}）：管理角色判定 —— course:* 权限码无 review
 *       专用码，且安全链的 JwtAuthenticationConverter 只把 permissions claim 映射为
 *       authority（无 ROLE_ 前缀，hasRole 不可达），故参照规格「管理角色」语义在服务层
 *       按 JWT roles claim 判定 SYSTEM_ADMIN/SUPER_ADMIN（组合模式与 TeacherAccessGuard
 *       的「服务内硬规则」对齐；长期方案 V005 加 course:review:hide 权限码 +
 *       @PreAuthorize，见规格 §15 决策点）；非管理角色 → 403 COURSE_ACCESS_DENIED；
 *       软隐藏（status → HIDDEN，保留审计行）后重算；已隐藏重复删幂等返回现状；
 *       隐藏用窄更新（仅 status/updated_by/updated_at WHERE id=?），学生并发改分
 *       不会被陈旧实体整行回写覆盖（P1b 竞态修复）；</li>
 *   <li>重算：{@code SELECT AVG(rating), COUNT(*) FROM course_review WHERE course_id=?
 *       AND status='VISIBLE'} → courseMapper.updateRatingSummary（聚合列直写，与
 *       incrementEnrollmentCount 同一风格：不动乐观锁 version）。</li>
 *   <li>学生重复提交与已隐藏评价（规格 §15 决策点）：upsert 固定写 status=VISIBLE，
 *       管理端隐藏后学生再次提交即恢复可见（视为重新评价）；维持现状，不做
 *       「隐藏后禁止复活」，见规格 §15 记录。</li>
 * </ul></p>
 */
@Service
public class CourseReviewService {

    public static final String STATUS_VISIBLE = "VISIBLE";
    public static final String STATUS_HIDDEN = "HIDDEN";
    public static final String ENROLLMENT_ACTIVE = "ACTIVE";

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;
    private static final BigDecimal ZERO_RATING_AVG = BigDecimal.ZERO.setScale(2);

    /** 管理端隐藏评价允许的角色（规格 §6「管理角色」；权限码无 review 专用码）。 */
    private static final Set<String> ADMIN_ROLES = Set.of("SYSTEM_ADMIN", "SUPER_ADMIN");

    private final CourseMapper courseMapper;
    private final CourseEnrollmentMapper enrollmentMapper;
    private final CourseReviewMapper reviewMapper;

    public CourseReviewService(
            CourseMapper courseMapper,
            CourseEnrollmentMapper enrollmentMapper,
            CourseReviewMapper reviewMapper) {
        this.courseMapper = Objects.requireNonNull(courseMapper, "courseMapper");
        this.enrollmentMapper = Objects.requireNonNull(enrollmentMapper, "enrollmentMapper");
        this.reviewMapper = Objects.requireNonNull(reviewMapper, "reviewMapper");
    }

    /**
     * 学生 upsert 自己对该课程的评价（POST /courses/{id}/reviews，登录即可，
     * 服务层校验 ACTIVE 选课）。返回以库中真实行为准。
     */
    @Transactional
    public CourseReviewResponse upsert(Long courseId, Long studentId, ReviewUpsertRequest request) {
        Objects.requireNonNull(request, "request");
        requireValidRating(request.rating());
        CourseEntity course = courseMapper.selectByIdForUpdate(courseId);
        if (course == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "Course not found: " + courseId);
        }
        requireActiveEnrollment(courseId, studentId);

        LocalDateTime now = LocalDateTime.now();
        CourseReviewEntity review = new CourseReviewEntity();
        // 自定义 INSERT ... ON DUPLICATE 不经 BaseMapper.insert，MP 的 ASSIGN_ID 不生效，
        // 显式 IdWorker 雪花 ID（命中 uk 更新分支不改 id，重查后以真实行为准返回）。
        review.setId(IdWorker.getId());
        review.setCourseId(courseId);
        review.setStudentId(studentId);
        review.setRating(request.rating());
        review.setContent(request.content());
        review.setStatus(STATUS_VISIBLE);
        review.setCreatedBy(studentId);
        review.setCreatedAt(now);
        review.setUpdatedBy(studentId);
        review.setUpdatedAt(now);
        reviewMapper.upsert(review);

        recalculate(course);

        CourseReviewEntity saved = reviewMapper.selectOne(new LambdaQueryWrapper<CourseReviewEntity>()
                .eq(CourseReviewEntity::getCourseId, courseId)
                .eq(CourseReviewEntity::getStudentId, studentId));
        if (saved == null) {
            throw new IllegalStateException(
                    "review upserted but not readable for course " + courseId + " student " + studentId);
        }
        return toResponse(saved);
    }

    /**
     * 管理端隐藏评价（DELETE /course-reviews/{id}）：置 HIDDEN（软隐藏保留审计）并
     * 按 VISIBLE 评价重算汇总；已隐藏重复删幂等返回现状。
     */
    @Transactional
    public CourseReviewResponse hide(Long reviewId, Long adminUserId, Set<String> roles) {
        requireAdmin(roles);
        CourseReviewEntity review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException(CourseErrorCode.REVIEW_NOT_FOUND,
                    "Course review not found: " + reviewId);
        }
        if (STATUS_HIDDEN.equals(review.getStatus())) {
            // 幂等：已隐藏直接返回现状，不做任何写。
            return toResponse(review);
        }
        // 锁课程根行：与 upsert 共用行锁串行化重算（并发隐藏/改评不丢汇总更新）。
        CourseEntity course = courseMapper.selectByIdForUpdate(review.getCourseId());
        if (course == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "Course not found for review: " + review.getCourseId());
        }
        // P1b 竞态修复：窄更新只写 status/updated_by/updated_at（WHERE id=?），
        // 不用陈旧实体整行 updateById —— 学生并发改分（rating/content）不会被覆盖。
        LocalDateTime now = LocalDateTime.now();
        reviewMapper.updateStatus(reviewId, STATUS_HIDDEN, adminUserId, now);
        review.setStatus(STATUS_HIDDEN);
        review.setUpdatedBy(adminUserId);
        review.setUpdatedAt(now);
        recalculate(course);
        return toResponse(review);
    }

    /** 同事务重算评分汇总列：只统计 VISIBLE 评价（HIDDEN 不计入）。 */
    private void recalculate(CourseEntity course) {
        CourseReviewSummaryRow summary = reviewMapper.selectVisibleSummary(course.getId());
        BigDecimal ratingAvg = summary == null || summary.getRatingAvg() == null
                ? ZERO_RATING_AVG
                : summary.getRatingAvg();
        int ratingCount = summary == null || summary.getRatingCount() == null
                ? 0
                : summary.getRatingCount().intValue();
        courseMapper.updateRatingSummary(course.getId(), ratingAvg, ratingCount);
    }

    private void requireActiveEnrollment(Long courseId, Long studentId) {
        Long count = enrollmentMapper.selectCount(new LambdaQueryWrapper<CourseEnrollmentEntity>()
                .eq(CourseEnrollmentEntity::getCourseId, courseId)
                .eq(CourseEnrollmentEntity::getStudentId, studentId)
                .eq(CourseEnrollmentEntity::getStatus, ENROLLMENT_ACTIVE));
        if (count == null || count == 0L) {
            throw new BusinessException(CourseErrorCode.NOT_ENROLLED,
                    "Student is not enrolled in the course: " + courseId);
        }
    }

    /** rating 越界兜底（入口 @Valid 已挡，防御非 HTTP 调用方）。 */
    private static void requireValidRating(Integer rating) {
        if (rating == null || rating < MIN_RATING || rating > MAX_RATING) {
            throw new BusinessException(CommonErrorCode.VALIDATION_FAILED,
                    "rating must be between 1 and 5");
        }
    }

    /** 管理角色判定：JWT roles claim 含 SYSTEM_ADMIN 或 SUPER_ADMIN（见类注释）。 */
    private static void requireAdmin(Set<String> roles) {
        if (roles == null || roles.stream().noneMatch(ADMIN_ROLES::contains)) {
            throw new BusinessException(CourseErrorCode.COURSE_ACCESS_DENIED,
                    "Review hiding requires SYSTEM_ADMIN or SUPER_ADMIN role");
        }
    }

    /** 实体 → 响应映射（CourseCatalogService 详情 reviews 列表复用）。 */
    static CourseReviewResponse toResponse(CourseReviewEntity review) {
        return new CourseReviewResponse(
                String.valueOf(review.getId()),
                String.valueOf(review.getStudentId()),
                review.getRating(),
                review.getContent(),
                review.getStatus(),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }
}
