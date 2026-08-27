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
 *   <li>自审拒绝：approve/reject 时 submitted_by == 审核人 → COURSE_ACCESS_DENIED 403；</li>
 *   <li>归档终态门禁（规格 §15）：lifecycle=ARCHIVED 是终态（归档且不可再销售），
 *       submit/approve 均 → COURSE_STATE_CONFLICT 409，防残留指针/残留待审绕过
 *       republish 的 OFFLINE 门禁复活。</li>
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
    /** 生命周期终态：归档（ARCHIVED）后不可再销售，任何复活路径一律 409。 */
    private static final String LIFECYCLE_ARCHIVED = "ARCHIVED";

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
     *
     * <p>归档终态门禁（规格 §15）：lifecycle=ARCHIVED 的课程已归档且不可再销售，即使
     * 残留 DRAFT 版本指针也不可提交（防御残留指针复活路径）→ COURSE_STATE_CONFLICT 409。</p>
     */
    @Transactional
    public CourseAuditResponse submitForReview(Long versionId, Long teacherId, Set<String> roles) {
        CourseVersionEntity version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "Course version not found: " + versionId);
        }
        CourseEntity course = requireCourse(version.getCourseId());
        teacherAccessGuard.requireAccess(course.getId(), teacherId);
        if (LIFECYCLE_ARCHIVED.equals(course.getLifecycleStatus())) {
            throw new BusinessException(CourseErrorCode.COURSE_STATE_CONFLICT,
                    "Archived course cannot submit for review (archived is terminal, course cannot be resold): " + course.getId());
        }
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

        // BUG-052 修复：记录提交前生命周期，供驳回/撤回恢复原状态——
        // 已发布课程迭代被驳回/撤回后旧版本继续在售（PUBLISHED），而非打回 DRAFT。
        course.setPreSubmitLifecycleStatus(course.getLifecycleStatus());
        course.setLifecycleStatus(LIFECYCLE_PENDING_REVIEW);
        int rootUpdated = courseMapper.updateById(course);
        if (rootUpdated == 0) {
            throw new BusinessException(CommonErrorCode.VERSION_CONFLICT,
                    "Course root changed concurrently: " + course.getId());
        }

        version.setVersionStatus(CourseVersionStateMachine.PENDING_REVIEW);
        auditWriter.write("SUBMIT_FOR_REVIEW", "course_version", String.valueOf(versionId),
                teacherId, roles, "SUCCESS", null);
        return CourseAuditResponse.from(submission, course, version);
    }

    /**
     * 审批通过并原子发布（POST /course-audits/{id}/approve）：锁根 → 自审拒绝 →
     * submission PENDING→APPROVED → published_version_id 切换 → 旧发布版本 SUPERSEDED →
     * 新版本 PUBLISHED → lifecycle=PUBLISHED + published_at → outbox CoursePublished，
     * 同一本地事务提交。aggregateVersion 取课程根更新后的乐观锁版本。
     *
     * <p>归档终态门禁（规格 §15）：lifecycle=ARCHIVED 的课程已归档且不可再销售，审批
     * 不得把归档课程发布回 PUBLISHED（与 submit 同为复活路径的双保险）→
     * COURSE_STATE_CONFLICT 409。</p>
     */
    @Transactional
    public CourseAuditResponse approve(Long auditId, Long reviewerId, Set<String> roles) {
        CourseAuditSubmissionEntity submission = requireSubmission(auditId);
        requireNotSelfReview(submission, reviewerId);
        AuditStateMachine.requireTransition(submission.getStatus(), SUBMISSION_APPROVED);

        CourseEntity course = courseMapper.selectByIdForUpdate(submission.getCourseId());
        if (course == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "Course not found: " + submission.getCourseId());
        }
        if (LIFECYCLE_ARCHIVED.equals(course.getLifecycleStatus())) {
            throw new BusinessException(CourseErrorCode.COURSE_STATE_CONFLICT,
                    "Archived course cannot be approved for publication (archived is terminal, course cannot be resold): " + course.getId());
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
        // BUG-052 修复：审批发布后无待恢复状态，一并清空提交前生命周期。
        LambdaUpdateWrapper<CourseEntity> rootUpdate = new LambdaUpdateWrapper<CourseEntity>()
                .eq(CourseEntity::getId, course.getId())
                .set(CourseEntity::getDraftVersionId, null)
                .set(CourseEntity::getPreSubmitLifecycleStatus, null);
        int rootUpdated = courseMapper.update(course, rootUpdate);
        if (rootUpdated == 0) {
            throw new BusinessException(CommonErrorCode.VERSION_CONFLICT,
                    "Course root changed concurrently: " + course.getId());
        }
        eventPublisher.coursePublished(course.getId(), version.getId(), course.getVersion(), now);
        courseMetrics.recordCoursePublished();
        courseMetrics.recordAuditApproved();
        auditWriter.write("AUDIT_APPROVED", "course_audit", String.valueOf(auditId),
                reviewerId, roles, "SUCCESS", null);

        version.setVersionStatus(CourseVersionStateMachine.PUBLISHED);
        return CourseAuditResponse.from(submission, course, version);
    }

    /**
     * 驳回（POST /course-audits/{id}/reject）：原因必填（400）；submission REJECTED +
     * reviewed_by/reviewed_at；版本 REJECTED；course.draft_version_id 保留指向 REJECTED
     * 版本（可复制新草稿）；BUG-052：lifecycle 恢复提交前状态（无记录回落 DRAFT）。
     */
    @Transactional
    public CourseAuditResponse reject(Long auditId, Long reviewerId, String reason, Set<String> roles) {
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
        // BUG-052 修复：恢复提交前生命周期（已发布课程旧版本继续在售），无记录
        // 回落 DRAFT；恢复值只接受白名单（防脏数据破坏状态机）。
        course.setLifecycleStatus(restorePreSubmitLifecycle(course));
        course.setPreSubmitLifecycleStatus(null);
        // BUG-053 同源修复：清空 pre_submit 必须用 wrapper 显式 set(null)，
        // updateById 的 NOT_NULL 策略会把 null 字段排除在 SET 之外；乐观锁语义
        // 与 approve 的 update(entity, wrapper) 模式一致。
        int rootUpdated = courseMapper.update(course, new LambdaUpdateWrapper<CourseEntity>()
                .eq(CourseEntity::getId, course.getId())
                .set(CourseEntity::getPreSubmitLifecycleStatus, null));
        if (rootUpdated == 0) {
            throw new BusinessException(CommonErrorCode.VERSION_CONFLICT,
                    "Course root changed concurrently: " + course.getId());
        }
        courseMetrics.recordAuditRejected();
        auditWriter.write("AUDIT_REJECTED", "course_audit", String.valueOf(auditId),
                reviewerId, roles, "SUCCESS", reason);

        version.setVersionStatus(CourseVersionStateMachine.REJECTED);
        return CourseAuditResponse.from(submission, course, version);
    }

    /**
     * 撤回（POST /course-audits/{id}/withdraw）：仅提交教师本人（submitted_by == 当前
     * userId，非本人 COURSE_ACCESS_DENIED 403）；PENDING→WITHDRAWN（含 withdrawn_at），
     * 版本 WITHDRAWN 不可再审批；BUG-052：lifecycle 恢复提交前状态（无记录回落 DRAFT）。
     *
     * <p>任务 22 规格审查：撤回同时清空 course.draft_version_id（WITHDRAWN 不可编辑），
     * 编辑页经 GET draft 404 落入「从最近版本创建草稿」恢复路径；重建草稿的复制源包含
     * WITHDRAWN（见 CourseVersionService.createDraftFromPublishedOrRejected），首次提交
     * 即撤回的课程不会 404 卡死。</p>
     */
    @Transactional
    public CourseAuditResponse withdraw(Long auditId, Long teacherId, Set<String> roles) {
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

        // BUG-052 修复：恢复提交前生命周期（已发布课程旧版本继续在售），无记录回落 DRAFT。
        course.setLifecycleStatus(restorePreSubmitLifecycle(course));
        course.setPreSubmitLifecycleStatus(null);
        // 任务 22 规格审查：撤回后草稿指针清空（WITHDRAWN 版本不可编辑），编辑页走
        // 「从最近版本创建草稿」恢复路径（复制源含 WITHDRAWN，见 CourseVersionService）。
        // BUG-053 修复：MyBatis-Plus 默认 updateStrategy=NOT_NULL，updateById 会把
        // null 字段排除在 SET 之外，draft_version_id 清不掉（与 approve 的
        // LambdaUpdateWrapper.set(..., null) 同坑）；显式 set(null) 一并清空
        // 草稿指针与提交前生命周期。
        course.setDraftVersionId(null);
        int rootUpdated = courseMapper.update(course, new LambdaUpdateWrapper<CourseEntity>()
                .eq(CourseEntity::getId, course.getId())
                .set(CourseEntity::getDraftVersionId, null)
                .set(CourseEntity::getPreSubmitLifecycleStatus, null));
        if (rootUpdated == 0) {
            throw new BusinessException(CommonErrorCode.VERSION_CONFLICT,
                    "Course root changed concurrently: " + course.getId());
        }
        auditWriter.write("SUBMISSION_WITHDRAWN", "course_audit", String.valueOf(auditId),
                teacherId, roles, "SUCCESS", null);

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
                .map(submission -> {
                    CourseEntity course = courseMapper.selectById(submission.getCourseId());
                    CourseVersionEntity version = versionMapper.selectById(submission.getCourseVersionId());
                    CourseVersionEntity publishedVersion = (course != null && course.getPublishedVersionId() != null)
                            ? versionMapper.selectById(course.getPublishedVersionId()) : null;
                    return CourseAuditResponse.from(submission, course, version, publishedVersion);
                })
                .toList();
        return PageResponse.of(items, page, pageSize, result.getTotal());
    }

    /** 审核快照与历史（GET /course-audits/{id}）：提交记录 + 课程/版本快照。 */
    public CourseAuditResponse getDetail(Long auditId) {
        CourseAuditSubmissionEntity submission = requireSubmission(auditId);
        CourseEntity course = courseMapper.selectById(submission.getCourseId());
        CourseVersionEntity version = versionMapper.selectById(submission.getCourseVersionId());
        CourseVersionEntity publishedVersion = (course != null && course.getPublishedVersionId() != null)
                ? versionMapper.selectById(course.getPublishedVersionId()) : null;
        return CourseAuditResponse.from(submission, course, version, publishedVersion);
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

    /** 驳回/撤回可恢复的提交前生命周期白名单（其余值一律回落 DRAFT）。 */
    private static final Set<String> RESTORABLE_LIFECYCLES = Set.of(
            LIFECYCLE_DRAFT, LIFECYCLE_PUBLISHED, "OFFLINE");

    /**
     * BUG-052 修复：驳回/撤回时恢复提交前生命周期——已发布课程迭代被驳回/撤回后
     * 旧版本继续在售（PUBLISHED），下架课程保持下架（OFFLINE）；无记录（历史数据
     * 或首次提交）或值不在白名单时回落 DRAFT。
     */
    private static String restorePreSubmitLifecycle(CourseEntity course) {
        String preSubmit = course.getPreSubmitLifecycleStatus();
        if (preSubmit == null || !RESTORABLE_LIFECYCLES.contains(preSubmit)) {
            return LIFECYCLE_DRAFT;
        }
        return preSubmit;
    }
}
