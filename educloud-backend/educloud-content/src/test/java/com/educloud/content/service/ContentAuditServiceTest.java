package com.educloud.content.service;

import com.educloud.content.entity.ContentAuditSubmissionEntity;
import com.educloud.content.entity.ContentRevisionEntity;
import com.educloud.content.entity.CourseContentEntity;
import com.educloud.content.mapper.ContentAuditSubmissionMapper;
import com.educloud.content.mapper.ContentRevisionMapper;
import com.educloud.content.mapper.CourseContentMapper;
import com.educloud.content.messaging.ContentEventPublisher;
import com.educloud.content.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentAuditServiceTest {

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.registerTableInfo(
                CourseContentEntity.class,
                ContentRevisionEntity.class,
                ContentAuditSubmissionEntity.class);
    }

    @Mock
    private ContentAuditSubmissionMapper submissionMapper;
    @Mock
    private ContentRevisionMapper revisionMapper;
    @Mock
    private CourseContentMapper contentMapper;
    @Mock
    private ContentEventPublisher eventPublisher;

    @InjectMocks
    private ContentAuditService auditService;

    @Test
    void approveAudit_atomicallyUpdatesStatusAndPublishesEvent() {
        ContentAuditSubmissionEntity submission = new ContentAuditSubmissionEntity();
        submission.setId(501L);
        submission.setCourseId(101L);
        submission.setContentRevisionId(202L);
        submission.setRevisionNo(2);
        submission.setStatus("PENDING");

        ContentRevisionEntity targetRevision = new ContentRevisionEntity();
        targetRevision.setId(202L);
        targetRevision.setCourseId(101L);
        targetRevision.setRevisionNo(2);
        targetRevision.setRevisionStatus("PENDING_REVIEW");

        CourseContentEntity contentRoot = new CourseContentEntity();
        contentRoot.setId(301L);
        contentRoot.setCourseId(101L);
        contentRoot.setPublishedRevisionId(201L);
        contentRoot.setAggregateVersion(1L);

        ContentRevisionEntity oldPublished = new ContentRevisionEntity();
        oldPublished.setId(201L);
        oldPublished.setRevisionStatus("PUBLISHED");

        when(submissionMapper.selectById(501L)).thenReturn(submission);
        when(revisionMapper.selectById(202L)).thenReturn(targetRevision);
        when(contentMapper.selectOne(any())).thenReturn(contentRoot);
        when(revisionMapper.selectById(201L)).thenReturn(oldPublished);

        auditService.approveAudit(501L, 9002L);

        assertThat(oldPublished.getRevisionStatus()).isEqualTo("SUPERSEDED");
        assertThat(targetRevision.getRevisionStatus()).isEqualTo("PUBLISHED");
        assertThat(contentRoot.getPublishedRevisionId()).isEqualTo(202L);
        assertThat(contentRoot.getAggregateVersion()).isEqualTo(2L);
        assertThat(submission.getStatus()).isEqualTo("APPROVED");

        verify(eventPublisher).contentRevisionPublished(
                eq(101L), eq(301L), eq(202L), eq(2), eq(2L), any(LocalDateTime.class));
    }
}
