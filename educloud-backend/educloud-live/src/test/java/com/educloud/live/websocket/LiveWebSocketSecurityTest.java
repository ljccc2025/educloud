package com.educloud.live.websocket;

import com.educloud.live.entity.LiveMessageEntity;
import com.educloud.live.entity.LiveRoomEntity;
import com.educloud.live.enums.LiveMessageType;
import com.educloud.live.enums.LiveSenderRole;
import com.educloud.live.exception.LiveErrorCode;
import com.educloud.live.exception.LiveException;
import com.educloud.live.mapper.LiveRoomMapper;
import com.educloud.live.service.LiveMessageService;
import com.educloud.live.service.LiveTicketService;
import com.educloud.live.websocket.model.LiveTicketPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiveWebSocketSecurityTest {

    @Mock
    private LiveTicketService liveTicketService;
    @Mock
    private LiveBroadcastService broadcastService;
    @Mock
    private LiveMessageService liveMessageService;
    @Mock
    private LiveRoomMapper liveRoomMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ServerHttpRequest request;
    @Mock
    private ServerHttpResponse response;
    @Mock
    private WebSocketHandler wsHandler;
    @Mock
    private WebSocketSession session;

    private LiveWebSocketInterceptor interceptor;
    private LiveWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        interceptor = new LiveWebSocketInterceptor(liveTicketService);
        handler = new LiveWebSocketHandler(broadcastService, liveMessageService, liveRoomMapper, stringRedisTemplate);
    }

    @Test
    void testBeforeHandshakeValidTicket() {
        when(request.getURI()).thenReturn(URI.create("http://localhost:8080/ws/v1/live/1001?ticket=valid_ticket_123"));
        LiveTicketPayload payload = LiveTicketPayload.builder()
                .roomId(1001L)
                .userId(3001L)
                .role(LiveSenderRole.STUDENT)
                .nickname("测试学员")
                .build();
        when(liveTicketService.verifyAndConsumeTicket(1001L, "valid_ticket_123")).thenReturn(payload);

        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertTrue(result);
        assertEquals(1001L, attributes.get(LiveWebSocketInterceptor.ATTR_ROOM_ID));
        assertEquals(3001L, attributes.get(LiveWebSocketInterceptor.ATTR_USER_ID));
        assertEquals("测试学员", attributes.get(LiveWebSocketInterceptor.ATTR_USER_NAME));
        assertEquals(LiveSenderRole.STUDENT, attributes.get(LiveWebSocketInterceptor.ATTR_USER_ROLE));
    }

    @Test
    void testBeforeHandshakeInvalidTicket() {
        when(request.getURI()).thenReturn(URI.create("http://localhost:8080/ws/v1/live/1001?ticket=expired_ticket"));
        when(liveTicketService.verifyAndConsumeTicket(1001L, "expired_ticket"))
                .thenThrow(new LiveException(LiveErrorCode.LIVE_TICKET_EXPIRED_OR_INVALID));

        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertFalse(result);
    }

    @Test
    void testHandleTextMessageChatWhenMuted() throws Exception {
        Map<String, Object> attributes = Map.of(
                LiveWebSocketInterceptor.ATTR_ROOM_ID, 1001L,
                LiveWebSocketInterceptor.ATTR_USER_ID, 3001L,
                LiveWebSocketInterceptor.ATTR_USER_NAME, "小明",
                LiveWebSocketInterceptor.ATTR_USER_ROLE, LiveSenderRole.STUDENT
        );
        when(session.getAttributes()).thenReturn(attributes);

        LiveRoomEntity room = LiveRoomEntity.builder()
                .id(1001L)
                .allowChat(false)
                .build();
        when(liveRoomMapper.selectById(1001L)).thenReturn(room);

        TextMessage incoming = new TextMessage("{\"type\":\"CHAT\",\"content\":\"老师好\"}");
        handler.handleTextMessage(session, incoming);

        verify(session).sendMessage(argThat(msg -> msg != null && msg.getPayload().toString().contains("LIVE_CHAT_MUTED")));
        verifyNoInteractions(liveMessageService);
    }

    @Test
    void testHandleTextMessageChatSuccess() throws Exception {
        Map<String, Object> attributes = Map.of(
                LiveWebSocketInterceptor.ATTR_ROOM_ID, 1001L,
                LiveWebSocketInterceptor.ATTR_USER_ID, 3001L,
                LiveWebSocketInterceptor.ATTR_USER_NAME, "小明",
                LiveWebSocketInterceptor.ATTR_USER_ROLE, LiveSenderRole.STUDENT
        );
        when(session.getAttributes()).thenReturn(attributes);

        LiveRoomEntity room = LiveRoomEntity.builder()
                .id(1001L)
                .allowChat(true)
                .build();
        when(liveRoomMapper.selectById(1001L)).thenReturn(room);

        LiveMessageEntity saved = LiveMessageEntity.builder()
                .id(5001L)
                .roomId(1001L)
                .sessionId(8001L)
                .senderId(3001L)
                .senderName("小明")
                .senderRole(LiveSenderRole.STUDENT)
                .messageType(LiveMessageType.CHAT)
                .content("老师好")
                .sentAt(LocalDateTime.now())
                .build();
        when(liveMessageService.saveMessage(eq(1001L), eq(3001L), eq("小明"), eq(LiveSenderRole.STUDENT), eq(LiveMessageType.CHAT), eq("老师好")))
                .thenReturn(saved);

        TextMessage incoming = new TextMessage("{\"type\":\"CHAT\",\"content\":\"老师好\"}");
        handler.handleTextMessage(session, incoming);

        verify(broadcastService).broadcastToRoom(eq(1001L), argThat(msg -> "CHAT".equals(msg.getType())));
    }
}
