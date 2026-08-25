package com.educloud.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.common.api.PageResponse;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.content.dto.response.ContentAuditResponse;
import com.educloud.content.entity.ContentAuditSubmissionEntity;
import com.educloud.content.entity.ContentRevisionEntity;
import com.educloud.content.entity.CourseContentEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.ContentAuditSubmissionMapper;
import com.educloud.content.mapper.ContentRevisionMapper;
import com.educloud.content.mapper.CourseContentMapper;
import com.educloud.content.messaging.ContentEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContentAuditService {

    private final ContentAuditSubmissionMapper submissionMapper;
    private final ContentRevisionMapper revisionMapper;
    private final CourseContentMapper contentMapper;
    private final ContentEventPublisher eventPublisher;

    public PageResponse<ContentAuditResponse> listAudits(String status, int page, int size) {
        Page<ContentAuditSubmissionEntity> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<ContentAuditSubmissionEntity> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(ContentAuditSubmissionEntity::getStatus, status);
        }
        wrapper.orderByDesc(ContentAuditSubmissionEntity::getSubmittedAt);

        Page<ContentAuditSubmissionEntity> pageResult = submissionMapper.selectPage(pageParam, wrapper);
        List<ContentAuditResponse> list = pageResult.getRecords().stream().map(this::toResponse).toList();
        return PageResponse.of(list, (int) pageResult.getCurrent(), (int) pageResult.getSize(), pageResult.getTotal());
    }

    public ContentAuditResponse getAuditDetail(Long auditId) {
        ContentAuditSubmissionEntity submission = submissionMapper.selectById(auditId);
        if (submission == null) {
            throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND, "Audit submission not found");
        }
        return toResponse(submission);
    }

    @Transactional
    public void approveAudit(Long auditId, Long adminId) {
        ContentAuditSubmissionEntity submission = submissionMapper.selectById(auditId);
        if (submission == null) {
            throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND, "Audit submission not found");
        }
        if (!"PENDING".equals(submission.getStatus())) {
            throw new BusinessException(ContentErrorCode.SUBMISSION_NOT_PENDING, "Audit submission is not in pending state");
        }

        ContentRevisionEntity targetRevision = revisionMapper.selectById(submission.getContentRevisionId());
        if (targetRevision == null) {
            throw new BusinessException(ContentErrorCode.REVISION_NOT_FOUND, "Revision not found");
        }

        CourseContentEntity contentRoot = contentMapper.selectOne(
                new LambdaQueryWrapper<CourseContentEntity>().eq(CourseContentEntity::getCourseId, submission.getCourseId()));
        if (contentRoot == null) {
            throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND, "Course content root not found");
        }

        LocalDateTime now = LocalDateTime.now();

        // 1. Supersede previous published revision
        if (contentRoot.getPublishedRevisionId() != null && !contentRoot.getPublishedRevisionId().equals(targetRevision.getId())) {
            ContentRevisionEntity oldPublished = revisionMapper.selectById(contentRoot.getPublishedRevisionId());
            if (oldPublished != null) {
                oldPublished.setRevisionStatus("SUPERSEDED");
                revisionMapper.updateById(oldPublished);
            }
        }

        // 2. Publish target revision
        targetRevision.setRevisionStatus("PUBLISHED");
        targetRevision.setPublishedAt(now);
        revisionMapper.updateById(targetRevision);

        // 3. Update course content root（BUG-006 修复：aggregateVersion 带有
        // @Version，由 OptimisticLockerInnerInterceptor 自动递增，禁止手工 +1；
        // updateById 返回 0 即版本冲突（并发审核同一内容根）→ 409 让调用方重试）。
        // 拦截器会回写 entity 上的新版本号，后续事件发布拿到的是递增后的值。
        contentRoot.setPublishedRevisionId(targetRevision.getId());
        contentRoot.setUpdatedAt(now);
        if (contentMapper.updateById(contentRoot) != 1) {
            throw new BusinessException(CommonErrorCode.VERSION_CONFLICT,
                    "Course content root was modified concurrently, please retry");
        }

        // 4. Update audit submission
        submission.setStatus("APPROVED");
        submission.setReviewedBy(adminId);
        submission.setReviewedAt(now);
        submissionMapper.updateById(submission);

        // 5. Publish domain event to Outbox
        eventPublisher.contentRevisionPublished(
                contentRoot.getCourseId(),
                contentRoot.getId(),
                targetRevision.getId(),
                targetRevision.getRevisionNo(),
                contentRoot.getAggregateVersion(),
                now);
    }

    @Transactional
    public void rejectAudit(Long auditId, String rejectReason, Long adminId) {
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new BusinessException(ContentErrorCode.AUDIT_REJECT_REASON_REQUIRED, "Reject reason is required");
        }

        ContentAuditSubmissionEntity submission = submissionMapper.selectById(auditId);
        if (submission == null) {
            throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND, "Audit submission not found");
        }
        if (!"PENDING".equals(submission.getStatus())) {
            throw new BusinessException(ContentErrorCode.SUBMISSION_NOT_PENDING, "Audit submission is not in pending state");
        }

        ContentRevisionEntity targetRevision = revisionMapper.selectById(submission.getContentRevisionId());
        if (targetRevision != null) {
            targetRevision.setRevisionStatus("REJECTED");
            revisionMapper.updateById(targetRevision);
        }

        LocalDateTime now = LocalDateTime.now();
        submission.setStatus("REJECTED");
        submission.setRejectReason(rejectReason);
        submission.setReviewedBy(adminId);
        submission.setReviewedAt(now);
        submissionMapper.updateById(submission);
    }

    private ContentAuditResponse toResponse(ContentAuditSubmissionEntity entity) {
        ContentAuditResponse r = new ContentAuditResponse();
        r.setId(entity.getId());
        r.setCourseId(entity.getCourseId());
        r.setContentRevisionId(entity.getContentRevisionId());
        r.setRevisionNo(entity.getRevisionNo());
        r.setSnapshotJson(entity.getSnapshotJson());
        r.setStatus(entity.getStatus());
        r.setSubmittedBy(entity.getSubmittedBy());
        r.setReviewedBy(entity.getReviewedBy());
        r.setRejectReason(entity.getRejectReason());
        r.setSubmittedAt(entity.getSubmittedAt());
        r.setReviewedAt(entity.getReviewedAt());
        r.setWithdrawnAt(entity.getWithdrawnAt());
        return r;
    }
}
