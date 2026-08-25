package com.educloud.live.service;

import com.educloud.live.config.LiveProperties;
import com.educloud.live.dto.response.LiveTicketResponse;
import com.educloud.live.entity.LiveRoomEntity;
import com.educloud.live.enums.LiveSenderRole;
import com.educloud.live.exception.LiveErrorCode;
import com.educloud.live.exception.LiveException;
import com.educloud.live.feign.CourseClient;
import com.educloud.live.feign.dto.CourseEnrollmentStatusResponse;
import com.educloud.live.mapper.LiveRoomMapper;
import com.educloud.live.service.impl.LiveTicketServiceImpl;
import com.educloud.live.websocket.model.LiveTicketPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiveTicketServiceTest {

    @Mock
    private LiveRoomMapper liveRoomMapper;
    @Mock
    private CourseClient courseClient;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private LiveProperties liveProperties;
    private LiveTicketService liveTicketService;

    @BeforeEach
    void setUp() {
        liveProperties = new LiveProperties();
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        liveTicketService = new LiveTicketServiceImpl(liveRoomMapper, courseClient, stringRedisTemplate, liveProperties);
    }

    @Test
    void testIssueTicketForTeacherSuccess() {
        LiveRoomEntity room = LiveRoomEntity.builder()
                .id(1001L)
                .courseId(2001L)
                .teacherId(9001L)
                .build();
        when(liveRoomMapper.selectById(1001L)).thenReturn(room);

        LiveTicketResponse response = liveTicketService.issueConnectionTicket(1001L, 9001L, "王老师", true);
        assertNotNull(response);
        assertEquals(1001L, response.getRoomId());
        assertNotNull(response.getTicket());
        assertTrue(response.getWsEndpoint().contains("ticket=" + response.getTicket()));

        verify(valueOperations).set(startsWith("educloud:live:ticket:"), anyString(), any(Duration.class));
        verifyNoInteractions(courseClient);
    }

    @Test
    void testIssueTicketForStudentEnrolledSuccess() {
        LiveRoomEntity room = LiveRoomEntity.builder()
                .id(1001L)
                .courseId(2001L)
                .teacherId(9001L)
                .build();
        when(liveRoomMapper.selectById(1001L)).thenReturn(room);

        CourseEnrollmentStatusResponse enrollment = CourseEnrollmentStatusResponse.builder()
                .courseId("2001")
                .studentId("3001")
                .status("ACTIVE")
                .enrolled(true)
                .build();
        when(courseClient.getEnrollmentStatus(eq(2001L), eq(3001L), any(), any())).thenReturn(enrollment);

        LiveTicketResponse response = liveTicketService.issueConnectionTicket(1001L, 3001L, "小明", false);
        assertNotNull(response);
        assertEquals(1001L, response.getRoomId());
        verify(courseClient).getEnrollmentStatus(eq(2001L), eq(3001L), any(), any());
    }

    @Test
    void testIssueTicketForStudentNotEnrolledFail() {
        LiveRoomEntity room = LiveRoomEntity.builder()
                .id(1001L)
                .courseId(2001L)
                .teacherId(9001L)
                .build();
        when(liveRoomMapper.selectById(1001L)).thenReturn(room);

        CourseEnrollmentStatusResponse enrollment = CourseEnrollmentStatusResponse.builder()
                .courseId("2001")
                .studentId("3001")
                .status("REVOKED")
                .enrolled(false)
                .build();
        when(courseClient.getEnrollmentStatus(eq(2001L), eq(3001L), any(), any())).thenReturn(enrollment);

        LiveException exception = assertThrows(LiveException.class, () ->
                liveTicketService.issueConnectionTicket(1001L, 3001L, "小明", false));
        assertEquals(LiveErrorCode.COURSE_NOT_ENROLLED, exception.getErrorCode());
    }

    @Test
    void testVerifyAndConsumeTicketSuccess() {
        String ticket = "test_ticket_uuid_123";
        String payloadJson = "{\"roomId\":1001,\"userId\":3001,\"role\":\"STUDENT\",\"nickname\":\"小明\"}";
        when(valueOperations.getAndDelete("educloud:live:ticket:" + ticket)).thenReturn(payloadJson);

        LiveTicketPayload payload = liveTicketService.verifyAndConsumeTicket(1001L, ticket);
        assertNotNull(payload);
        assertEquals(1001L, payload.getRoomId());
        assertEquals(3001L, payload.getUserId());
        assertEquals(LiveSenderRole.STUDENT, payload.getRole());
        assertEquals("小明", payload.getNickname());
    }

    @Test
    void testVerifyAndConsumeTicketReplayFail() {
        String ticket = "test_ticket_consumed";
        when(valueOperations.getAndDelete("educloud:live:ticket:" + ticket)).thenReturn(null);

        LiveException exception = assertThrows(LiveException.class, () ->
                liveTicketService.verifyAndConsumeTicket(1001L, ticket));
        assertEquals(LiveErrorCode.LIVE_TICKET_EXPIRED_OR_INVALID, exception.getErrorCode());
    }
}
