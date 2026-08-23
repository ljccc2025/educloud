package com.educloud.course.dto.response;

import com.educloud.course.entity.CourseAuditSubmissionEntity;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseVersionEntity;

import java.time.LocalDateTime;

/**
 * 课程审核响应（M05 任务 9）：审核提交快照 + 课程/版本快照（列表/详情/审批/驳回/撤回共用）。
 *
 * <p>依据：规格 §6 —— Snowflake ID（auditId/courseId/versionId/coverFileId/categoryId）
 * 一律 String（前端禁止 Number()）；price 金额序列化为字符串；版本内部不可变字段
 * （title/level 等）作为审核上下文下发，但课程详情 API 不下发本快照（§6 课程详情契约）。</p>
 */
public record CourseAuditResponse(
        String auditId,
        String courseId,
        String versionId,
        Integer versionNo,
        String title,
        String subtitle,
        String description,
        String coverFileId,
        String level,
        String price,
        String currency,
        String categoryId,
        String versionStatus,
        String lifecycleStatus,
        String submissionStatus,
        String submittedBy,
        LocalDateTime submittedAt,
        LocalDateTime withdrawnAt,
        String reviewedBy,
        LocalDateTime reviewedAt,
        String reason) {

    public static CourseAuditResponse from(
            CourseAuditSubmissionEntity submission, CourseEntity course, CourseVersionEntity version) {
        return new CourseAuditResponse(
                String.valueOf(submission.getId()),
                String.valueOf(submission.getCourseId()),
                String.valueOf(submission.getCourseVersionId()),
                version == null ? null : version.getVersionNo(),
                version == null ? null : version.getTitle(),
                version == null ? null : version.getSubtitle(),
                version == null ? null : version.getDescription(),
                version == null || version.getCoverFileId() == null
                        ? null : String.valueOf(version.getCoverFileId()),
                version == null ? null : version.getLevel(),
                version == null || version.getPrice() == null
                        ? null : version.getPrice().toPlainString(),
                version == null ? null : version.getCurrency(),
                version == null || version.getCategoryId() == null
                        ? null : String.valueOf(version.getCategoryId()),
                version == null ? null : version.getVersionStatus(),
                course == null ? null : course.getLifecycleStatus(),
                submission.getStatus(),
                String.valueOf(submission.getSubmittedBy()),
                submission.getSubmittedAt(),
                submission.getWithdrawnAt(),
                submission.getReviewedBy() == null ? null : String.valueOf(submission.getReviewedBy()),
                submission.getReviewedAt(),
                submission.getReason());
    }
}
