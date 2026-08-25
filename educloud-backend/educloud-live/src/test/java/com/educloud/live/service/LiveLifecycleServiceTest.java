package com.educloud.live.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.id.IdentifierGenerator;
import com.educloud.live.config.LiveProperties;
import com.educloud.live.dto.request.LiveRoomCreateRequest;
import com.educloud.live.dto.request.LiveRoomUpdateRequest;
import com.educloud.live.dto.response.LiveEndResponse;
import com.educloud.live.dto.response.LiveRoomDetailResponse;
import com.educloud.live.dto.response.LiveStartResponse;
import com.educloud.live.entity.LiveReplayEntity;
import com.educloud.live.entity.LiveRoomEntity;
import com.educloud.live.entity.LiveSessionEntity;
import com.educloud.live.enums.LiveProviderType;
import com.educloud.live.enums.LiveRoomStatus;
import com.educloud.live.enums.LiveSessionStatus;
import com.educloud.live.exception.LiveErrorCode;
import com.educloud.live.exception.LiveException;
import com.educloud.live.mapper.LiveReplayMapper;
import com.educloud.live.mapper.LiveRoomMapper;
import com.educloud.live.mapper.LiveSessionMapper;
import com.educloud.live.service.impl.LiveLifecycleServiceImpl;
import com.educloud.live.spi.LiveStreamProviderFactory;
import com.educloud.live.spi.plugins.MockLiveStreamProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiveLifecycleServiceTest {

    @Mock
    private LiveRoomMapper liveRoomMapper;
    @Mock
    private LiveSessionMapper liveSessionMapper;
    @Mock
    private LiveReplayMapper liveReplayMapper;
    @Mock
    private IdentifierGenerator identifierGenerator;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private SetOperations<String, String> setOperations;

    private LiveLifecycleService liveLifecycleService;

    @BeforeEach
    void setUp() {
        LiveProperties properties = new LiveProperties();
        properties.setEnvironment("dev");
        MockLiveStreamProvider mockProvider = new MockLiveStreamProvider(properties);
        LiveStreamProviderFactory factory = new LiveStreamProviderFactory(List.of(mockProvider));

        liveLifecycleService = new LiveLifecycleServiceImpl(
                liveRoomMapper,
                liveSessionMapper,
                liveReplayMapper,
                factory,
                identifierGenerator,
                stringRedisTemplate
        );
    }

    @Test
    void testCreateRoomSuccess() {
        when(identifierGenerator.nextId()).thenReturn(10001L);
        when(liveRoomMapper.insert(any(LiveRoomEntity.class))).thenReturn(1);

        LocalDateTime now = LocalDateTime.now();
        LiveRoomCreateRequest request = LiveRoomCreateRequest.builder()
                .courseId(20001L)
                .title("Spring Cloud 实战")
                .description("测试直播")
                .scheduledStartAt(now.plusHours(1))
                .scheduledEndAt(now.plusHours(2))
                .build();

        LiveRoomDetailResponse response = liveLifecycleService.createRoom(9001L, false, request);
        assertNotNull(response);
        assertEquals(10001L, response.getId());
        assertEquals(20001L, response.getCourseId());
        assertEquals(9001L, response.getTeacherId());
        assertEquals(LiveRoomStatus.CREATED, response.getStatus());

        verify(liveRoomMapper).insert(any(LiveRoomEntity.class));
    }

    @Test
    void testCreateRoomTimeInvalid() {
        LocalDateTime now = LocalDateTime.now();
        LiveRoomCreateRequest request = LiveRoomCreateRequest.builder()
                .courseId(20001L)
                .title("Spring Cloud 实战")
                .scheduledStartAt(now.plusHours(2))
                .scheduledEndAt(now.plusHours(1))
                .build();

        LiveException exception = assertThrows(LiveException.class, () ->
                liveLifecycleService.createRoom(9001L, false, request));
        assertEquals(LiveErrorCode.LIVE_ROOM_TIME_INVALID, exception.getErrorCode());
    }

    @Test
    void testTeacherOwnershipIDOR() {
        LiveRoomEntity room = LiveRoomEntity.builder()
                .id(10001L)
                .teacherId(9001L)
                .status(LiveRoomStatus.CREATED)
                .build();
        when(liveRoomMapper.selectById(10001L)).thenReturn(room);

        LiveRoomUpdateRequest updateRequest = LiveRoomUpdateRequest.builder()
                .title("修改标题")
                .build();

        // 用户 9002 试图修改 9001 的房间
        LiveException exception = assertThrows(LiveException.class, () ->
                liveLifecycleService.updateRoom(10001L, 9002L, false, updateRequest));
        assertEquals(LiveErrorCode.LIVE_COURSE_NOT_OWNED, exception.getErrorCode());
    }

    @Test
    void testStartLiveSuccess() {
        LiveRoomEntity room = LiveRoomEntity.builder()
                .id(10001L)
                .teacherId(9001L)
                .status(LiveRoomStatus.CREATED)
                .providerType(LiveProviderType.MOCK)
                .streamKey("stream_10001")
                .build();
        when(liveRoomMapper.selectById(10001L)).thenReturn(room);
        when(liveRoomMapper.updateStatusCas(10001L, "CREATED", "LIVING")).thenReturn(1);
        when(identifierGenerator.nextId()).thenReturn(80001L);
        when(liveSessionMapper.insert(any(LiveSessionEntity.class))).thenReturn(1);

        LiveStartResponse startResponse = liveLifecycleService.startLive(10001L, 9001L, false);
        assertNotNull(startResponse);
        assertEquals(10001L, startResponse.getRoomId());
        assertEquals(80001L, startResponse.getSessionId());
        assertNotNull(startResponse.getPushInfo());
        assertTrue(startResponse.getPushInfo().getPushUrl().contains("stream_10001"));
    }

    @Test
    void testEndLiveSuccess() {
        LiveRoomEntity room = LiveRoomEntity.builder()
                .id(10001L)
                .teacherId(9001L)
                .title("直播课程")
                .status(LiveRoomStatus.LIVING)
                .build();
        LiveSessionEntity session = LiveSessionEntity.builder()
                .id(80001L)
                .roomId(10001L)
                .status(LiveSessionStatus.LIVING)
                .startedAt(LocalDateTime.now().minusHours(1))
                .peakViewers(50)
                .totalViewers(80)
                .build();

        when(liveRoomMapper.selectById(10001L)).thenReturn(room);
        when(liveRoomMapper.updateStatusCas(10001L, "LIVING", "ENDED")).thenReturn(1);
        when(liveSessionMapper.selectOne(any())).thenReturn(session);
        when(identifierGenerator.nextId()).thenReturn(70001L);
        when(liveReplayMapper.insert(any(LiveReplayEntity.class))).thenReturn(1);

        LiveEndResponse endResponse = liveLifecycleService.endLive(10001L, 9001L, false);
        assertNotNull(endResponse);
        assertEquals(10001L, endResponse.getRoomId());
        assertEquals(80001L, endResponse.getSessionId());
        assertEquals(70001L, endResponse.getReplayId());
        verify(liveReplayMapper).insert(any(LiveReplayEntity.class));
    }
}
