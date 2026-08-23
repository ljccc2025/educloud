package com.educloud.course.service;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.course.dto.request.CourseCreateRequest;
import com.educloud.course.dto.response.CourseDraftResponse;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseTeacherEntity;
import com.educloud.course.entity.CourseVersionEntity;
import com.educloud.course.exception.CourseErrorCode;
import com.educloud.course.mapper.CourseMapper;
import com.educloud.course.mapper.CourseTeacherMapper;
import com.educloud.course.mapper.CourseVersionMapper;
import com.educloud.course.messaging.CourseEventPublisher;
import com.educloud.course.support.SnowflakeIds;
import com.educloud.course.support.TeacherAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 课程聚合根服务（M05 任务 8/10）：建课（根 + 负责人关系 + 首版草稿同一事务）与
 * 生命周期操作（下架/重上架/归档：锁根 + 状态迁移 + outbox）。
 *
 * <p>任务 10 依据：规格 §6/§9 —— offline 仅 PUBLISHED→OFFLINE；republish 仅 OFFLINE 且
 * 有 published_version_id（M05 就绪 gate 恒放行，course_content_readiness_projection
 * 不参与判断）→ PUBLISHED，归档后不可重上架；archive 仅 OFFLINE→ARCHIVED（PUBLISHED
 * 必须先下架）；三操作均须 course_teacher 归属（TeacherAccessGuard）；生命周期非法 →
 * COURSE_STATE_CONFLICT 409。封面 bind 见任务 12（FileClient），本任务只存 cover_file_id 值。</p>
 */
@Service
public class CourseService {

    public static final String LIFECYCLE_DRAFT = "DRAFT";
    public static final String LIFECYCLE_PUBLISHED = "PUBLISHED";
    public static final String LIFECYCLE_OFFLINE = "OFFLINE";
    public static final String LIFECYCLE_ARCHIVED = "ARCHIVED";
    public static final String TEACHER_ROLE_OWNER = "OWNER";

    private final CourseMapper courseMapper;
    private final CourseTeacherMapper courseTeacherMapper;
    private final CourseVersionMapper courseVersionMapper;
    private final TeacherAccessGuard teacherAccessGuard;
    private final CourseEventPublisher eventPublisher;

    public CourseService(
            CourseMapper courseMapper,
            CourseTeacherMapper courseTeacherMapper,
            CourseVersionMapper courseVersionMapper,
            TeacherAccessGuard teacherAccessGuard,
            CourseEventPublisher eventPublisher) {
        this.courseMapper = Objects.requireNonNull(courseMapper, "courseMapper");
        this.courseTeacherMapper = Objects.requireNonNull(courseTeacherMapper, "courseTeacherMapper");
        this.courseVersionMapper = Objects.requireNonNull(courseVersionMapper, "courseVersionMapper");
        this.teacherAccessGuard = Objects.requireNonNull(teacherAccessGuard, "teacherAccessGuard");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    /**
     * 建课：同一事务插入 course（owner=当前教师，lifecycle=DRAFT）+ course_teacher(OWNER)
     * + course_version(version_no=1, DRAFT)；course.draft_version_id 指向新版本。
     * 主键由 MyBatis-Plus ASSIGN_ID 在 insert 时生成并回写实体。
     */
    @Transactional
    public CourseDraftResponse createCourse(Long teacherId, CourseCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();

        CourseEntity course = new CourseEntity();
        course.setOwnerTeacherId(teacherId);
        course.setLifecycleStatus(LIFECYCLE_DRAFT);
        // 与 course.version DEFAULT 0 对齐：insert 后立即可携带明确乐观锁条件更新根。
        course.setVersion(0L);
        course.setCreatedBy(teacherId);
        course.setCreatedAt(now);
        course.setUpdatedBy(teacherId);
        course.setUpdatedAt(now);
        courseMapper.insert(course);

        CourseTeacherEntity teacher = new CourseTeacherEntity();
        teacher.setCourseId(course.getId());
        teacher.setTeacherId(teacherId);
        teacher.setTeacherRole(TEACHER_ROLE_OWNER);
        teacher.setJoinedAt(now);
        courseTeacherMapper.insert(teacher);

        CourseVersionEntity draft = new CourseVersionEntity();
        draft.setCourseId(course.getId());
        draft.setVersionNo(1);
        draft.setCategoryId(SnowflakeIds.parse(request.categoryId(), "categoryId"));
        draft.setTitle(request.title());
        draft.setSubtitle(request.subtitle());
        draft.setDescription(request.description());
        draft.setCoverFileId(SnowflakeIds.parse(request.coverFileId(), "coverFileId"));
        draft.setLevel(request.level());
        draft.setPrice(request.price());
        draft.setCurrency(request.currency());
        draft.setVersionStatus(CourseVersionService.STATUS_DRAFT);
        draft.setCreatedBy(teacherId);
        draft.setCreatedAt(now);
        courseVersionMapper.insert(draft);

        course.setDraftVersionId(draft.getId());
        courseMapper.updateById(course);

        return CourseDraftResponse.from(course, draft,
                List.of(new CourseDraftResponse.Teacher(String.valueOf(teacherId), TEACHER_ROLE_OWNER)));
    }

    /**
     * 下架（POST /courses/{id}/offline，course:offline）：锁根 → 归属校验 →
     * 仅 PUBLISHED → lifecycle=OFFLINE → outbox CourseOfflined，同一事务。
     * 发布版本保持 PUBLISHED 不变（重上架直接复用），非法生命周期 → 409 COURSE_STATE_CONFLICT。
     */
    @Transactional
    public void offline(Long courseId, Long teacherId) {
        CourseEntity course = requireCourseForUpdate(courseId);
        teacherAccessGuard.requireAccess(course.getId(), teacherId);
        if (!LIFECYCLE_PUBLISHED.equals(course.getLifecycleStatus())) {
            throw new BusinessException(CourseErrorCode.COURSE_STATE_CONFLICT,
                    "Only a published course can be taken offline: " + courseId);
        }
        if (course.getPublishedVersionId() == null) {
            throw new BusinessException(CourseErrorCode.COURSE_STATE_CONFLICT,
                    "Published course has no published version: " + courseId);
        }
        LocalDateTime now = LocalDateTime.now();
        course.setLifecycleStatus(LIFECYCLE_OFFLINE);
        requireRootUpdate(course);
        eventPublisher.courseOfflined(courseId, course.getPublishedVersionId(), course.getVersion(), now);
    }

    /**
     * 重新上架（POST /courses/{id}/republish，course:republish）：锁根 → 归属校验 →
     * 仅 OFFLINE 且有 published_version_id → lifecycle=PUBLISHED +
     * published_at=最近一次（重）发布时间（重上架会刷新，利于 newest 排序）→
     * outbox CourseRepublished，同一事务。M05 就绪 gate 恒放行（规格 §3/§15：
     * course_content_readiness_projection 不参与判断，M06 启用）。OFFLINE 以外
     * （含归档后不可再销售）或无有效发布版本 → 409 COURSE_STATE_CONFLICT。
     */
    @Transactional
    public void republish(Long courseId, Long teacherId) {
        CourseEntity course = requireCourseForUpdate(courseId);
        teacherAccessGuard.requireAccess(course.getId(), teacherId);
        if (!LIFECYCLE_OFFLINE.equals(course.getLifecycleStatus())) {
            throw new BusinessException(CourseErrorCode.COURSE_STATE_CONFLICT,
                    "Only an offline course can be republished (archived courses cannot be resold): " + courseId);
        }
        if (course.getPublishedVersionId() == null) {
            throw new BusinessException(CourseErrorCode.COURSE_STATE_CONFLICT,
                    "Offline course has no published version to republish: " + courseId);
        }
        LocalDateTime now = LocalDateTime.now();
        course.setLifecycleStatus(LIFECYCLE_PUBLISHED);
        course.setPublishedAt(now);
        requireRootUpdate(course);
        eventPublisher.courseRepublished(courseId, course.getPublishedVersionId(), course.getVersion(), now);
    }

    /**
     * 归档（POST /courses/{id}/archive，course:archive）：锁根 → 归属校验 →
     * 仅 OFFLINE → lifecycle=ARCHIVED（不可再销售）→ outbox CourseArchived，同一事务。
     * PUBLISHED 直接归档必须先下架 → 409 COURSE_STATE_CONFLICT。
     */
    @Transactional
    public void archive(Long courseId, Long teacherId) {
        CourseEntity course = requireCourseForUpdate(courseId);
        teacherAccessGuard.requireAccess(course.getId(), teacherId);
        if (!LIFECYCLE_OFFLINE.equals(course.getLifecycleStatus())) {
            throw new BusinessException(CourseErrorCode.COURSE_STATE_CONFLICT,
                    "Only an offline course can be archived (published courses must be taken offline first): " + courseId);
        }
        LocalDateTime now = LocalDateTime.now();
        course.setLifecycleStatus(LIFECYCLE_ARCHIVED);
        requireRootUpdate(course);
        eventPublisher.courseArchived(courseId, course.getPublishedVersionId(), course.getVersion(), now);
    }

    /** 锁根读课程（SELECT ... FOR UPDATE，规格 §7 版本乐观锁）；不存在 → 404。 */
    private CourseEntity requireCourseForUpdate(Long courseId) {
        CourseEntity course = courseMapper.selectByIdForUpdate(courseId);
        if (course == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND, "Course not found: " + courseId);
        }
        return course;
    }

    /** 根乐观锁更新：@Version 拦截器写回新 version；影响行数 0（并发改根）→ 409。 */
    private void requireRootUpdate(CourseEntity course) {
        int updated = courseMapper.updateById(course);
        if (updated == 0) {
            throw new BusinessException(CommonErrorCode.VERSION_CONFLICT,
                    "Course root changed concurrently: " + course.getId());
        }
    }
}
