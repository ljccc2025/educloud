package com.educloud.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.common.api.PageResponse;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.course.dto.response.CourseAuditResponse;
import com.educloud.course.entity.CourseAuditSubmissionEntity;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseVersionEntity;
import com.educloud.course.exception.CourseErrorCode;
import com.educloud.course.mapper.CourseAuditSubmissionMapper;
import com.educloud.course.mapper.CourseMapper;
import com.educloud.course.mapper.CourseVersionMapper;
import com.educloud.course.messaging.CourseEventPublisher;
import com.educloud.course.observability.AuditWriter;
import com.educloud.course.observability.CourseMetrics;
import com.educloud.course.state.AuditStateMachine;
import com.educloud.course.state.CourseVersionStateMachine;
import com.educloud.course.support.TeacherAccessGuard;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 课程审核服务（M05 任务 9）：提交/审批（原子发布）/驳回/撤回 + 待审列表/详情。
 *
 * <p>依据：规格 §6/§7 与任务 9 关键实现点 ——
 * <ul>
 *   <li>submit：归属校验 + DRAFT→PENDING_REVIEW 条件更新 + 写 PENDING submission；</li>
 *   <li>approve：SELECT course FOR UPDATE → submission APPROVED → published_version_id
 *       切换 → 旧发布版本 SUPERSEDED → lifecycle=PUBLISHED + published_at → outbox
 *       CoursePublished，全部同一本地事务提交（同事务原子发布）；</li>
 *   <li>reject：原因必填（400 REVIEW_REJECT_REASON_REQUIRED）→ REJECTED，
 *       course.draft_version_id 保留指向 REJECTED 版本（可复制新草稿）；</li>
 *   <li>withdraw：仅提交教师本人（submitted_by==当前 userId，否则 403）→ WITHDRAWN；</li>
 *   <li>自审拒绝：approve/reject 时 submitted_by == 审核人 → COURSE_ACCESS_DENIED 403。</li>
 * </ul>
 * 状态机是前置硬规则；并发兜底用条件更新（WHERE version_status=…）+ 根乐观锁。</p>
 */
@Service
public class CourseAuditService {

    public static final String SUBMISSION_PENDING = AuditStateMachine.PENDING;
    public static final String SUBMISSION_APPROVED = AuditStateMachine.APPROVED;
    public static final String SUBMISSION_REJECTED = AuditStateMachine.REJECTED;
    public static final String SUBMISSION_WITHDRAWN = AuditStateMachine.WITHDRAWN;

    private static final String LIFECYCLE_DRAFT = "DRAFT";
    private static final String LIFECYCLE_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String LIFECYCLE_PUBLISHED = "PUBLISHED";

    private final CourseMapper courseMapper;
    private final CourseVersionMapper versionMapper;
    private final CourseAuditSubmissionMapper submissionMapper;
    private final TeacherAccessGuard teacherAccessGuard;
    private final CourseEventPublisher eventPublisher;
    private final CourseMetrics courseMetrics;
    private final AuditWriter auditWriter;

    public CourseAuditService(
            CourseMapper courseMapper,
            CourseVersionMapper versionMapper,
            CourseAuditSubmissionMapper submissionMapper,
            TeacherAccessGuard teacherAccessGuard,
            CourseEventPublisher eventPublisher,
            CourseMetrics courseMetrics,
            AuditWriter auditWriter) {
        this.courseMapper = Objects.requireNonNull(courseMapper, "courseMapper");
        this.versionMapper = Objects.requireNonNull(versionMapper, "versionMapper");
        this.submissionMapper = Objects.requireNonNull(submissionMapper, "submissionMapper");
        this.teacherAccessGuard = Objects.requireNonNull(teacherAccessGuard, "teacherAccessGuard");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.courseMetrics = Objects.requireNonNull(courseMetrics, "courseMetrics");
        this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter");
    }

    /**
     * 提交审核（POST /course-drafts/{versionId}/submit-review）：归属校验 +
     * DRAFT→PENDING_REVIEW（条件更新防并发）+ 写 PENDING submission + 根 lifecycle 置
     * PENDING_REVIEW，同一事务。
     */
    @Transactional
    public CourseAuditResponse submitForReview(Long versionId, Long teacherId) {
        CourseVersionEntity version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "Course version not found: " + versionId);
        }
        CourseEntity course = requireCourse(version.getCourseId());
        teacherAccessGuard.requireAccess(course.getId(), teacherId);
        CourseVersionStateMachine.requireTransition(
                version.getVersionStatus(), CourseVersionStateMachine.PENDING_REVIEW);
        if (course.getDraftVersionId() == null || !course.getDraftVersionId().equals(versionId)) {
            throw new BusinessException(CourseErrorCode.VERSION_NOT_DRAFT,
                    "Version is not the current draft pointer of course: " + course.getId());
        }

        int updated = versionMapper.update(null, new LambdaUpdateWrapper<CourseVersionEntity>()
                .eq(CourseVersionEntity::getId, versionId)
                .eq(CourseVersionEntity::getVersionStatus, CourseVersionStateMachine.DRAFT)
                .set(CourseVersionEntity::getVersionStatus, CourseVersionStateMachine.PENDING_REVIEW));
        if (updated == 0) {
            throw new BusinessException(CourseErrorCode.VERSION_NOT_DRAFT,
                    "Course version is not in draft state");
        }

        CourseAuditSubmissionEntity submission = new CourseAuditSubmissionEntity();
        submission.setCourseId(course.getId());
        submission.setCourseVersionId(versionId);
        submission.setStatus(SUBMISSION_PENDING);
        submission.setSubmittedBy(teacherId);
        submission.setSubmittedAt(LocalDateTime.now());
        try {
            submissionMapper.insert(submission);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(CourseErrorCode.VERSION_NOT_DRAFT,
                    "Audit submission was created concurrently for version: " + versionId,
                    null, exception);
        }

        course.setLifecycleStatus(LIFECYCLE_PENDING_REVIEW);
        int rootUpdated = courseMapper.updateById(course);
        if (rootUpdated == 0) {
            throw new BusinessException(CommonErrorCode.VERSION_CONFLICT,
                    "Course root changed concurrently: " + course.getId());
        }

        version.setVersionStatus(CourseVersionStateMachine.PENDING_REVIEW);
        auditWriter.write("SUBMIT_FOR_REVIEW", "course_version", String.valueOf(versionId),
                teacherId, Set.of("TEACHER"), "SUCCESS", null);
        return CourseAuditResponse.from(submission, course, version);
    }

    /**
     * 审批通过并原子发布（POST /course-audits/{id}/approve）：锁根 → 自审拒绝 →
     * submission PENDING→APPROVED → published_version_id 切换 → 旧发布版本 SUPERSEDED →
     * 新版本 PUBLISHED → lifecycle=PUBLISHED + published_at → outbox CoursePublished，
     * 同一本地事务提交。aggregateVersion 取课程根更新后的乐观锁版本。
     */
    @Transactional
    public CourseAuditResponse approve(Long auditId, Long reviewerId) {
        CourseAuditSubmissionEntity submission = requireSubmission(auditId);
        requireNotSelfReview(submission, reviewerId);
        AuditStateMachine.requireTransition(submission.getStatus(), SUBMISSION_APPROVED);

        CourseEntity course = courseMapper.selectByIdForUpdate(submission.getCourseId());
        if (course == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "Course not found: " + submission.getCourseId());
        }
        CourseVersionEntity version = requireVersion(submission.getCourseVersionId());
        CourseVersionStateMachine.requireTransition(
                version.getVersionStatus(), CourseVersionStateMachine.PUBLISHED);

        LocalDateTime now = LocalDateTime.now();
        submission.setStatus(SUBMISSION_APPROVED);
        submission.setReviewedBy(reviewerId);
        submission.setReviewedAt(now);
        submissionMapper.updateById(submission);

        Long previousPublishedId = course.getPublishedVersionId();
        if (previousPublishedId != null && !previousPublishedId.equals(version.getId())) {
            int superseded = versionMapper.update(null, new LambdaUpdateWrapper<CourseVersionEntity>()
                    .eq(CourseVersionEntity::getId, previousPublishedId)
                    .eq(CourseVersionEntity::getVersionStatus, CourseVersionStateMachine.PUBLISHED)
                    .set(CourseVersionEntity::getVersionStatus, CourseVersionStateMachine.SUPERSEDED));
            if (superseded == 0) {
                throw new BusinessException(CourseErrorCode.VERSION_NOT_DRAFT,
                        "Previous published version is not in published state: " + previousPublishedId);
            }
        }
        int published = versionMapper.update(null, new LambdaUpdateWrapper<CourseVersionEntity>()
                .eq(CourseVersionEntity::getId, version.getId())
                .eq(CourseVersionEntity::getVersionStatus, CourseVersionStateMachine.PENDING_REVIEW)
                .set(CourseVersionEntity::getVersionStatus, CourseVersionStateMachine.PUBLISHED));
        if (published == 0) {
            throw new BusinessException(CourseErrorCode.VERSION_NOT_DRAFT,
                    "Course version is not pending review");
        }

        course.setPublishedVersionId(version.getId());
        course.setLifecycleStatus(LIFECYCLE_PUBLISHED);
        course.setPublishedAt(now);
        course.setDraftVersionId(null);
        // 清空草稿指针必须用 wrapper 显式 set(null)：MyBatis-Plus 默认 updateStrategy=NOT_NULL，
        // updateById(entity) 会把 null 字段排除在 SET 之外，draft_version_id 永远清不掉。
        // 根乐观锁：entity 携带 selectByIdForUpdate 加载的 version，OptimisticLockerInnerInterceptor
        // 对 update(entity, wrapper) 自动追加 version 条件并回写新 version（禁止手动 +1）。
        LambdaUpdateWrapper<CourseEntity> rootUpdate = new LambdaUpdateWrapper<CourseEntity>()
                .eq(CourseEntity::getId, course.getId())
                .set(CourseEntity::getDraftVersionId, null);
        int rootUpdated = courseMapper.update(course, rootUpdate);
        if (rootUpdated == 0) {
            throw new BusinessException(CommonErrorCode.VERSION_CONFLICT,
                    "Course root changed concurrently: " + course.getId());
        }
        eventPublisher.coursePublished(course.getId(), version.getId(), course.getVersion(), now);
        courseMetrics.recordCoursePublished();
        courseMetrics.recordAuditApproved();
        auditWriter.write("AUDIT_APPROVED", "course_audit", String.valueOf(auditId),
                reviewerId, Set.of("COURSE_REVIEWER"), "SUCCESS", null);

        version.setVersionStatus(CourseVersionStateMachine.PUBLISHED);
        return CourseAuditResponse.from(submission, course, version);
    }

    /**
     * 驳回（POST /course-audits/{id}/reject）：原因必填（400）；submission REJECTED +
     * reviewed_by/reviewed_at；版本 REJECTED；course.draft_version_id 保留指向 REJECTED
     * 版本（可复制新草稿），lifecycle 回到 DRAFT。
     */
    @Transactional
    public CourseAuditResponse reject(Long auditId, Long reviewerId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(CourseErrorCode.REVIEW_REJECT_REASON_REQUIRED,
                    "Reject reason is required");
        }
        CourseAuditSubmissionEntity submission = requireSubmission(auditId);
        requireNotSelfReview(submission, reviewerId);
        AuditStateMachine.requireTransition(submission.getStatus(), SUBMISSION_REJECTED);

        CourseEntity course = courseMapper.selectByIdForUpdate(submission.getCourseId());
        if (course == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "Course not found: " + submission.getCourseId());
        }
        CourseVersionEntity version = requireVersion(submission.getCourseVersionId());
        CourseVersionStateMachine.requireTransition(
                version.getVersionStatus(), CourseVersionStateMachine.REJECTED);

        LocalDateTime now = LocalDateTime.now();
        submission.setStatus(SUBMISSION_REJECTED);
        submission.setReviewedBy(reviewerId);
        submission.setReviewedAt(now);
        submission.setReason(reason);
        submissionMapper.updateById(submission);

        int updated = versionMapper.update(null, new LambdaUpdateWrapper<CourseVersionEntity>()
                .eq(CourseVersionEntity::getId, version.getId())
                .eq(CourseVersionEntity::getVersionStatus, CourseVersionStateMachine.PENDING_REVIEW)
                .set(CourseVersionEntity::getVersionStatus, CourseVersionStateMachine.REJECTED));
        if (updated == 0) {
            throw new BusinessException(CourseErrorCode.VERSION_NOT_DRAFT,
                    "Course version is not pending review");
        }

        course.setDraftVersionId(version.getId());
        course.setLifecycleStatus(LIFECYCLE_DRAFT);
        int rootUpdated = courseMapper.updateById(course);
        if (rootUpdated == 0) {
            throw new BusinessException(CommonErrorCode.VERSION_CONFLICT,
                    "Course root changed concurrently: " + course.getId());
        }
        courseMetrics.recordAuditRejected();
        auditWriter.write("AUDIT_REJECTED", "course_audit", String.valueOf(auditId),
                reviewerId, Set.of("COURSE_REVIEWER"), "SUCCESS", reason);

        version.setVersionStatus(CourseVersionStateMachine.REJECTED);
        return CourseAuditResponse.from(submission, course, version);
    }

    /**
     * 撤回（POST /course-audits/{id}/withdraw）：仅提交教师本人（submitted_by == 当前
     * userId，非本人 COURSE_ACCESS_DENIED 403）；PENDING→WITHDRAWN（含 withdrawn_at），
     * 版本 WITHDRAWN 不可再审批；lifecycle 回到 DRAFT。
     */
    @Transactional
    public CourseAuditResponse withdraw(Long auditId, Long teacherId) {
        CourseAuditSubmissionEntity submission = requireSubmission(auditId);
        if (!teacherId.equals(submission.getSubmittedBy())) {
            throw new BusinessException(CourseErrorCode.COURSE_ACCESS_DENIED,
                    "Only the submitting teacher can withdraw the audit submission");
        }
        AuditStateMachine.requireTransition(submission.getStatus(), SUBMISSION_WITHDRAWN);

        CourseEntity course = courseMapper.selectByIdForUpdate(submission.getCourseId());
        if (course == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "Course not found: " + submission.getCourseId());
        }
        CourseVersionEntity version = requireVersion(submission.getCourseVersionId());
        CourseVersionStateMachine.requireTransition(
                version.getVersionStatus(), CourseVersionStateMachine.WITHDRAWN);

        LocalDateTime now = LocalDateTime.now();
        submission.setStatus(SUBMISSION_WITHDRAWN);
        submission.setWithdrawnAt(now);
        submissionMapper.updateById(submission);

        int updated = versionMapper.update(null, new LambdaUpdateWrapper<CourseVersionEntity>()
                .eq(CourseVersionEntity::getId, version.getId())
                .eq(CourseVersionEntity::getVersionStatus, CourseVersionStateMachine.PENDING_REVIEW)
                .set(CourseVersionEntity::getVersionStatus, CourseVersionStateMachine.WITHDRAWN));
        if (updated == 0) {
            throw new BusinessException(CourseErrorCode.VERSION_NOT_DRAFT,
                    "Course version is not pending review");
        }

        course.setLifecycleStatus(LIFECYCLE_DRAFT);
        int rootUpdated = courseMapper.updateById(course);
        if (rootUpdated == 0) {
            throw new BusinessException(CommonErrorCode.VERSION_CONFLICT,
                    "Course root changed concurrently: " + course.getId());
        }

        version.setVersionStatus(CourseVersionStateMachine.WITHDRAWN);
        return CourseAuditResponse.from(submission, course, version);
    }

    /** 管理端待审核分页（GET /course-audits）：status=PENDING，按提交时间倒序。 */
    public PageResponse<CourseAuditResponse> listPending(int page, int pageSize) {
        Page<CourseAuditSubmissionEntity> result = submissionMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<CourseAuditSubmissionEntity>()
                        .eq(CourseAuditSubmissionEntity::getStatus, SUBMISSION_PENDING)
                        .orderByDesc(CourseAuditSubmissionEntity::getSubmittedAt));
        List<CourseAuditResponse> items = result.getRecords().stream()
                .map(submission -> CourseAuditResponse.from(
                        submission,
                        courseMapper.selectById(submission.getCourseId()),
                        versionMapper.selectById(submission.getCourseVersionId())))
                .toList();
        return PageResponse.of(items, page, pageSize, result.getTotal());
    }

    /** 审核快照与历史（GET /course-audits/{id}）：提交记录 + 课程/版本快照。 */
    public CourseAuditResponse getDetail(Long auditId) {
        CourseAuditSubmissionEntity submission = requireSubmission(auditId);
        return CourseAuditResponse.from(
                submission,
                courseMapper.selectById(submission.getCourseId()),
                versionMapper.selectById(submission.getCourseVersionId()));
    }

    private CourseAuditSubmissionEntity requireSubmission(Long auditId) {
        CourseAuditSubmissionEntity submission = submissionMapper.selectById(auditId);
        if (submission == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "Audit submission not found: " + auditId);
        }
        return submission;
    }

    private CourseVersionEntity requireVersion(Long versionId) {
        CourseVersionEntity version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "Course version not found: " + versionId);
        }
        return version;
    }

    private CourseEntity requireCourse(Long courseId) {
        CourseEntity course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND, "Course not found: " + courseId);
        }
        return course;
    }

    /** 审核角色不能审批自己的提交（规格 §9）：submitted_by == 当前 userId → 403。 */
    private static void requireNotSelfReview(CourseAuditSubmissionEntity submission, Long reviewerId) {
        if (reviewerId.equals(submission.getSubmittedBy())) {
            throw new BusinessException(CourseErrorCode.COURSE_ACCESS_DENIED,
                    "Reviewer cannot review their own submission");
        }
    }
}
