package com.educloud.content.service;

import com.educloud.common.error.BusinessException;
import com.educloud.common.id.IdentifierGenerator;
import com.educloud.content.dto.request.ProgressReportRequest;
import com.educloud.content.dto.response.CourseProgressResponse;
import com.educloud.content.entity.CourseCertificateEntity;
import com.educloud.content.entity.CourseContentEntity;
import com.educloud.content.entity.CoursewareEntity;
import com.educloud.content.entity.UserCourseProgressEntity;
import com.educloud.content.entity.UserCoursewareProgressEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.CourseContentMapper;
import com.educloud.content.mapper.CoursewareMapper;
import com.educloud.content.mapper.UserCourseProgressMapper;
import com.educloud.content.mapper.UserCoursewareProgressMapper;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseProgressServiceTest {

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.registerTableInfo(
                CoursewareEntity.class,
                UserCoursewareProgressEntity.class,
                CourseContentEntity.class,
                UserCourseProgressEntity.class);
    }

    @Mock
    private UserCoursewareProgressMapper coursewareProgressMapper;
    @Mock
    private UserCourseProgressMapper courseProgressMapper;
    @Mock
    private CoursewareMapper coursewareMapper;
    @Mock
    private CourseContentMapper contentMapper;
    @Mock
    private IdentifierGenerator idGenerator;
    @Mock
    private CertificateService certificateService;
    @Mock
    private ContentEventPublisher eventPublisher;
    @Mock
    private CourseClient courseClient;

    @InjectMocks
    private CourseProgressService progressService;

    @Test
    void reportProgress_rejectsInvalidDeltaSeconds() {
        CoursewareEntity cw = new CoursewareEntity();
        cw.setId(501L);
        cw.setCourseId(101L);
        cw.setDurationSeconds(300);
        cw.setStatus("ACTIVE");

        when(coursewareMapper.selectById(501L)).thenReturn(cw);

        ProgressReportRequest req = new ProgressReportRequest();
        req.setPositionSeconds(10);
        req.setWatchedDeltaSeconds(120); // Exceeds 60s max heartbeat limit

        assertThatThrownBy(() -> progressService.reportProgress(501L, req, 2001L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContentErrorCode.INVALID_PROGRESS);
    }

    @Test
    void reportProgress_aggregatesCourseProgressSuccessfully() {
        CoursewareEntity cw = new CoursewareEntity();
        cw.setId(501L);
        cw.setCourseId(101L);
        cw.setDurationSeconds(300);
        cw.setStatus("ACTIVE");

        CourseContentEntity content = new CourseContentEntity();
        content.setCourseId(101L);
        content.setPublishedRevisionId(201L);

        when(coursewareMapper.selectById(501L)).thenReturn(cw);
        when(coursewareProgressMapper.selectOne(any())).thenReturn(null);
        when(contentMapper.selectOne(any())).thenReturn(content);
        when(coursewareMapper.selectCount(any())).thenReturn(4L);
        when(coursewareProgressMapper.selectCount(any())).thenReturn(2L);
        when(courseProgressMapper.selectOne(any())).thenReturn(null);
        when(idGenerator.nextId()).thenReturn(901L, 902L);

        ProgressReportRequest req = new ProgressReportRequest();
        req.setPositionSeconds(300);
        req.setWatchedDeltaSeconds(15);
        req.setCompleted(true);

        CourseProgressResponse resp = progressService.reportProgress(501L, req, 2001L);

        assertThat(resp).isNotNull();
        assertThat(resp.getCourseId()).isEqualTo(101L);
        assertThat(resp.getCompletedCoursewareCount()).isEqualTo(2);
        assertThat(resp.getTotalCoursewareCount()).isEqualTo(4);
        assertThat(resp.getProgressPercent()).isEqualTo(50);
        verify(coursewareProgressMapper).insert(any(UserCoursewareProgressEntity.class));
        verify(courseProgressMapper).insert(any(UserCourseProgressEntity.class));
    }

    @Test
    void reportProgress_courseCompleted_issuesCertificateAndPublishesEvents() {
        CoursewareEntity cw = new CoursewareEntity();
        cw.setId(501L);
        cw.setCourseId(101L);
        cw.setDurationSeconds(300);
        cw.setStatus("ACTIVE");

        CourseContentEntity content = new CourseContentEntity();
        content.setCourseId(101L);
        content.setPublishedRevisionId(201L);

        when(coursewareMapper.selectById(501L)).thenReturn(cw);
        when(coursewareProgressMapper.selectOne(any())).thenReturn(null);
        when(contentMapper.selectOne(any())).thenReturn(content);
        when(coursewareMapper.selectCount(any())).thenReturn(2L);
        // 全部课件完成 → 进度 100%
        when(coursewareProgressMapper.selectCount(any())).thenReturn(2L);
        when(courseProgressMapper.selectOne(any())).thenReturn(null);
        when(idGenerator.nextId()).thenReturn(901L, 902L);

        when(certificateService.findCertificate(2001L, 101L)).thenReturn(null);
        when(courseClient.getCourseSnapshot(101L))
                .thenReturn(new CourseClient.CourseSnapshot("Spring Boot 微服务实践", 3001L));
        CourseCertificateEntity issued = new CourseCertificateEntity();
        issued.setCertNo("CERT-20260827-000001");
        issued.setUserId(2001L);
        issued.setCourseId(101L);
        issued.setIssuedAt(LocalDateTime.of(2026, 8, 27, 10, 0));
        when(certificateService.issueCertificate(eq(2001L), eq(101L), eq("Spring Boot 微服务实践")))
                .thenReturn(issued);

        ProgressReportRequest req = new ProgressReportRequest();
        req.setPositionSeconds(300);
        req.setWatchedDeltaSeconds(15);
        req.setCompleted(true);

        CourseProgressResponse resp = progressService.reportProgress(501L, req, 2001L);

        assertThat(resp.getProgressPercent()).isEqualTo(100);
        verify(certificateService).issueCertificate(2001L, 101L, "Spring Boot 微服务实践");
        verify(eventPublisher).courseCompleted(
                eq(101L), eq(2001L), eq("Spring Boot 微服务实践"), anyLong(), any(LocalDateTime.class));
        verify(eventPublisher).certificateIssued(
                eq("CERT-20260827-000001"), eq(101L), eq(2001L), eq(3001L),
                eq("Spring Boot 微服务实践"), anyLong(), eq(issued.getIssuedAt()));
    }

    @Test
    void reportProgress_certificateAlreadyIssued_isIdempotent() {
        CoursewareEntity cw = new CoursewareEntity();
        cw.setId(501L);
        cw.setCourseId(101L);
        cw.setDurationSeconds(300);
        cw.setStatus("ACTIVE");

        CourseContentEntity content = new CourseContentEntity();
        content.setCourseId(101L);
        content.setPublishedRevisionId(201L);

        when(coursewareMapper.selectById(501L)).thenReturn(cw);
        when(coursewareProgressMapper.selectOne(any())).thenReturn(null);
        when(contentMapper.selectOne(any())).thenReturn(content);
        when(coursewareMapper.selectCount(any())).thenReturn(2L);
        when(coursewareProgressMapper.selectCount(any())).thenReturn(2L);
        when(courseProgressMapper.selectOne(any())).thenReturn(null);
        when(idGenerator.nextId()).thenReturn(901L, 902L);

        CourseCertificateEntity existing = new CourseCertificateEntity();
        existing.setCertNo("CERT-20260827-000001");
        when(certificateService.findCertificate(2001L, 101L)).thenReturn(existing);

        ProgressReportRequest req = new ProgressReportRequest();
        req.setPositionSeconds(300);
        req.setWatchedDeltaSeconds(15);
        req.setCompleted(true);

        CourseProgressResponse resp = progressService.reportProgress(501L, req, 2001L);

        assertThat(resp.getProgressPercent()).isEqualTo(100);
        verify(certificateService, never()).issueCertificate(any(), any(), any());
        verify(eventPublisher, never()).courseCompleted(any(), any(), any(), anyLong(), any());
        verify(eventPublisher, never()).certificateIssued(any(), any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    void reportProgress_certificateIssuanceFailure_doesNotBlockProgressReport() {
        CoursewareEntity cw = new CoursewareEntity();
        cw.setId(501L);
        cw.setCourseId(101L);
        cw.setDurationSeconds(300);
        cw.setStatus("ACTIVE");

        CourseContentEntity content = new CourseContentEntity();
        content.setCourseId(101L);
        content.setPublishedRevisionId(201L);

        when(coursewareMapper.selectById(501L)).thenReturn(cw);
        when(coursewareProgressMapper.selectOne(any())).thenReturn(null);
        when(contentMapper.selectOne(any())).thenReturn(content);
        when(coursewareMapper.selectCount(any())).thenReturn(2L);
        when(coursewareProgressMapper.selectCount(any())).thenReturn(2L);
        when(courseProgressMapper.selectOne(any())).thenReturn(null);
        when(idGenerator.nextId()).thenReturn(901L, 902L);

        when(certificateService.findCertificate(2001L, 101L)).thenReturn(null);
        when(courseClient.getCourseSnapshot(101L))
                .thenReturn(new CourseClient.CourseSnapshot("Spring Boot 微服务实践", 3001L));
        when(certificateService.issueCertificate(any(), any(), any()))
                .thenThrow(new IllegalStateException("db down"));

        ProgressReportRequest req = new ProgressReportRequest();
        req.setPositionSeconds(300);
        req.setWatchedDeltaSeconds(15);
        req.setCompleted(true);

        // 证书生成失败不阻断进度上报：仍返回 100% 进度响应，不发事件。
        CourseProgressResponse resp = progressService.reportProgress(501L, req, 2001L);

        assertThat(resp).isNotNull();
        assertThat(resp.getProgressPercent()).isEqualTo(100);
        verify(eventPublisher, never()).courseCompleted(any(), any(), any(), anyLong(), any());
        verify(eventPublisher, never()).certificateIssued(any(), any(), any(), any(), any(), anyLong(), any());
    }
}
