package com.educloud.content.service;

import com.educloud.common.error.BusinessException;
import com.educloud.common.id.IdentifierGenerator;
import com.educloud.content.dto.request.ProgressReportRequest;
import com.educloud.content.dto.response.CourseProgressResponse;
import com.educloud.content.entity.CourseContentEntity;
import com.educloud.content.entity.CoursewareEntity;
import com.educloud.content.entity.UserCourseProgressEntity;
import com.educloud.content.entity.UserCoursewareProgressEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.CourseContentMapper;
import com.educloud.content.mapper.CoursewareMapper;
import com.educloud.content.mapper.UserCourseProgressMapper;
import com.educloud.content.mapper.UserCoursewareProgressMapper;
import com.educloud.content.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
}
