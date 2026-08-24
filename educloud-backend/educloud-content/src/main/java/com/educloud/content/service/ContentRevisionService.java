package com.educloud.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.common.id.IdentifierGenerator;
import com.educloud.content.dto.response.ChapterResponse;
import com.educloud.content.entity.ContentAuditSubmissionEntity;
import com.educloud.content.entity.ContentRevisionEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.ContentAuditSubmissionMapper;
import com.educloud.content.mapper.ContentRevisionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContentRevisionService {

    private final ContentRevisionMapper revisionMapper;
    private final ContentAuditSubmissionMapper submissionMapper;
    private final CourseContentService courseContentService;
    private final IdentifierGenerator idGenerator;
    private final ObjectMapper objectMapper;

    @Transactional
    public void submitReview(Long revisionId, Long teacherId) {
        ContentRevisionEntity revision = revisionMapper.selectById(revisionId);
        if (revision == null) {
            throw new BusinessException(ContentErrorCode.REVISION_NOT_FOUND, "Content revision not found");
        }
        if (!"DRAFT".equals(revision.getRevisionStatus())) {
            throw new BusinessException(ContentErrorCode.REVISION_NOT_DRAFT, "Only DRAFT revision can be submitted for review");
        }

        List<ChapterResponse> chapterTree = courseContentService.buildChapterTree(revisionId, revision.getCourseId(), null);
        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(chapterTree);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize chapter tree snapshot", e);
        }

        revision.setRevisionStatus("PENDING_REVIEW");
        revision.setSubmittedAt(LocalDateTime.now());
        revisionMapper.updateById(revision);

        ContentAuditSubmissionEntity submission = submissionMapper.selectOne(
                new LambdaQueryWrapper<ContentAuditSubmissionEntity>()
                        .eq(ContentAuditSubmissionEntity::getContentRevisionId, revisionId));
        if (submission == null) {
            submission = new ContentAuditSubmissionEntity();
            submission.setId(idGenerator.nextId());
            submission.setCourseId(revision.getCourseId());
            submission.setContentRevisionId(revisionId);
            submission.setRevisionNo(revision.getRevisionNo());
            submission.setSnapshotJson(snapshotJson);
            submission.setStatus("PENDING");
            submission.setSubmittedBy(teacherId);
            submission.setSubmittedAt(LocalDateTime.now());
            submissionMapper.insert(submission);
        } else {
            submission.setStatus("PENDING");
            submission.setSnapshotJson(snapshotJson);
            submission.setSubmittedBy(teacherId);
            submission.setSubmittedAt(LocalDateTime.now());
            submission.setReviewedBy(null);
            submission.setReviewedAt(null);
            submission.setRejectReason(null);
            submissionMapper.updateById(submission);
        }
    }

    @Transactional
    public void withdrawReview(Long revisionId, Long teacherId) {
        ContentRevisionEntity revision = revisionMapper.selectById(revisionId);
        if (revision == null) {
            throw new BusinessException(ContentErrorCode.REVISION_NOT_FOUND, "Content revision not found");
        }
        if (!"PENDING_REVIEW".equals(revision.getRevisionStatus())) {
            throw new BusinessException(ContentErrorCode.SUBMISSION_NOT_PENDING, "Revision is not in pending review state");
        }

        revision.setRevisionStatus("WITHDRAWN");
        revisionMapper.updateById(revision);

        ContentAuditSubmissionEntity submission = submissionMapper.selectOne(
                new LambdaQueryWrapper<ContentAuditSubmissionEntity>()
                        .eq(ContentAuditSubmissionEntity::getContentRevisionId, revisionId));
        if (submission != null) {
            submission.setStatus("WITHDRAWN");
            submission.setWithdrawnAt(LocalDateTime.now());
            submissionMapper.updateById(submission);
        }
    }
}
