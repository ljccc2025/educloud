package com.educloud.live.service;

import com.educloud.live.config.LiveProperties;
import com.educloud.live.dto.response.LiveReplayResponse;
import com.educloud.live.entity.LiveReplayEntity;
import com.educloud.live.entity.LiveRoomEntity;
import com.educloud.live.enums.LiveReplayStatus;
import com.educloud.live.exception.LiveErrorCode;
import com.educloud.live.exception.LiveException;
import com.educloud.live.feign.CourseClient;
import com.educloud.live.feign.FileClient;
import com.educloud.live.feign.dto.CourseEnrollmentStatusResponse;
import com.educloud.live.feign.dto.DownloadGrantResponse;
import com.educloud.live.mapper.LiveReplayMapper;
import com.educloud.live.mapper.LiveRoomMapper;
import com.educloud.live.service.impl.LiveReplayServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiveReplayServiceTest {

    @Mock
    private LiveReplayMapper liveReplayMapper;
    @Mock
    private LiveRoomMapper liveRoomMapper;
    @Mock
    private CourseClient courseClient;
    @Mock
    private FileClient fileClient;

    private LiveProperties liveProperties;
    private LiveReplayService liveReplayService;

    @BeforeEach
    void setUp() {
        liveProperties = new LiveProperties();
        liveReplayService = new LiveReplayServiceImpl(liveReplayMapper, liveRoomMapper, courseClient, fileClient, liveProperties);
    }

    @Test
    void testGetReplayDetailForStudentEnrolledSuccess() {
        LiveReplayEntity replay = LiveReplayEntity.builder()
                .id(901L)
                .roomId(1001L)
                .sessionId(801L)
                .fileId(5001L)
                .title("回放视频")
                .durationSeconds(3600L)
                .status(LiveReplayStatus.AVAILABLE)
                .build();
        when(liveReplayMapper.selectById(901L)).thenReturn(replay);

        LiveRoomEntity room = LiveRoomEntity.builder()
                .id(1001L)
                .courseId(2001L)
                .teacherId(8888L)
                .build();
        when(liveRoomMapper.selectById(1001L)).thenReturn(room);

        CourseEnrollmentStatusResponse enrollment = CourseEnrollmentStatusResponse.builder()
                .courseId("2001")
                .studentId("3001")
                .status("ACTIVE")
                .enrolled(true)
                .build();
        when(courseClient.getEnrollmentStatus(eq(2001L), eq(3001L), any(), any())).thenReturn(enrollment);

        DownloadGrantResponse grant = DownloadGrantResponse.builder()
                .downloadUrl("http://cdn.educloud.cn/signed/replay_5001.mp4?token=abc")
                .build();
        when(fileClient.grantSingle(eq(5001L), any(), any(), any())).thenReturn(grant);

        LiveReplayResponse response = liveReplayService.getReplayDetail(901L, 3001L, false);
        assertNotNull(response);
        assertEquals(901L, response.getId());
        assertEquals("http://cdn.educloud.cn/signed/replay_5001.mp4?token=abc", response.getPlayUrl());
    }

    @Test
    void testGetReplayDetailForStudentNotEnrolledFail() {
        LiveReplayEntity replay = LiveReplayEntity.builder()
                .id(901L)
                .roomId(1001L)
                .sessionId(801L)
                .fileId(5001L)
                .status(LiveReplayStatus.AVAILABLE)
                .build();
        when(liveReplayMapper.selectById(901L)).thenReturn(replay);

        LiveRoomEntity room = LiveRoomEntity.builder()
                .id(1001L)
                .courseId(2001L)
                .teacherId(8888L)
                .build();
        when(liveRoomMapper.selectById(1001L)).thenReturn(room);

        CourseEnrollmentStatusResponse enrollment = CourseEnrollmentStatusResponse.builder()
                .courseId("2001")
                .studentId("3001")
                .status("CANCELLED")
                .enrolled(false)
                .build();
        when(courseClient.getEnrollmentStatus(eq(2001L), eq(3001L), any(), any())).thenReturn(enrollment);

        LiveException exception = assertThrows(LiveException.class, () ->
                liveReplayService.getReplayDetail(901L, 3001L, false));
        assertEquals(LiveErrorCode.COURSE_NOT_ENROLLED, exception.getErrorCode());
    }

    @Test
    void testDeleteReplayIDOR() {
        LiveReplayEntity replay = LiveReplayEntity.builder()
                .id(901L)
                .roomId(1001L)
                .status(LiveReplayStatus.AVAILABLE)
                .build();
        when(liveReplayMapper.selectById(901L)).thenReturn(replay);

        LiveRoomEntity room = LiveRoomEntity.builder()
                .id(1001L)
                .teacherId(8888L)
                .build();
        when(liveRoomMapper.selectById(1001L)).thenReturn(room);

        LiveException exception = assertThrows(LiveException.class, () ->
                liveReplayService.deleteReplay(901L, 3001L, false));
        assertEquals(LiveErrorCode.LIVE_COURSE_NOT_OWNED, exception.getErrorCode());
    }
}
