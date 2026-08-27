package com.educloud.course.dto.response;

import com.educloud.course.entity.CourseAuditSubmissionEntity;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseVersionEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 课程审核响应（M05 任务 9）：审核提交快照 + 课程/版本快照 + 智能变更识别 Diff（列表/详情/审批/驳回/撤回共用）。
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
        String reason,
        String changeSummary,
        List<String> changes,
        String previousCoverFileId,
        String previousPrice,
        String previousTitle) {

    public static CourseAuditResponse from(
            CourseAuditSubmissionEntity submission, CourseEntity course, CourseVersionEntity version) {
        return from(submission, course, version, null);
    }

    public static CourseAuditResponse from(
            CourseAuditSubmissionEntity submission, CourseEntity course, CourseVersionEntity version, CourseVersionEntity previousPublishedVersion) {
        List<String> diffList = new ArrayList<>();
        if (previousPublishedVersion == null || previousPublishedVersion.getId().equals(version != null ? version.getId() : null)) {
            diffList.add("✨ 新课首次发布");
        } else if (version != null) {
            if (!Objects.equals(version.getCoverFileId(), previousPublishedVersion.getCoverFileId())) {
                diffList.add("📷 修改课程封面");
            }
            if (version.getPrice() != null && (previousPublishedVersion.getPrice() == null || version.getPrice().compareTo(previousPublishedVersion.getPrice()) != 0)) {
                diffList.add("💰 调整定价 (¥" + (previousPublishedVersion.getPrice() == null ? "0.00" : previousPublishedVersion.getPrice().toPlainString()) + " → ¥" + version.getPrice().toPlainString() + ")");
            }
            if (!Objects.equals(version.getTitle(), previousPublishedVersion.getTitle())) {
                diffList.add("📝 修改标题 (" + previousPublishedVersion.getTitle() + " → " + version.getTitle() + ")");
            }
            if (!Objects.equals(version.getSubtitle(), previousPublishedVersion.getSubtitle())) {
                diffList.add("📝 修改副标题");
            }
            if (!Objects.equals(version.getDescription(), previousPublishedVersion.getDescription())) {
                diffList.add("📄 更新课程简介");
            }
            if (!Objects.equals(version.getLevel(), previousPublishedVersion.getLevel())) {
                diffList.add("🎯 调整难度 (" + previousPublishedVersion.getLevel() + " → " + version.getLevel() + ")");
            }
            if (!Objects.equals(version.getCategoryId(), previousPublishedVersion.getCategoryId())) {
                diffList.add("🏷️ 调整课程分类");
            }
            if (diffList.isEmpty()) {
                diffList.add("📦 课程内容迭代与更新");
            }
        }
        String summary = String.join("，", diffList);

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
                submission.getReason(),
                summary,
                diffList,
                previousPublishedVersion == null || previousPublishedVersion.getCoverFileId() == null ? null : String.valueOf(previousPublishedVersion.getCoverFileId()),
                previousPublishedVersion == null || previousPublishedVersion.getPrice() == null ? null : previousPublishedVersion.getPrice().toPlainString(),
                previousPublishedVersion == null ? null : previousPublishedVersion.getTitle());
    }
}
