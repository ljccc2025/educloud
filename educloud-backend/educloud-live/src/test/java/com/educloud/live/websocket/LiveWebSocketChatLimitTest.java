package com.educloud.live.websocket;

import com.educloud.live.entity.LiveMessageEntity;
import com.educloud.live.entity.LiveRoomEntity;
import com.educloud.live.enums.LiveMessageType;
import com.educloud.live.enums.LiveSenderRole;
import com.educloud.live.mapper.LiveRoomMapper;
import com.educloud.live.service.LiveMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

/**
 * 弹幕长度与频率限制测试
 * 覆盖：长度超限拒绝、5 秒内重复发送拒绝、正常发送通过、控制消息不受限。
 */
@ExtendWith(MockitoExtension.class)
class LiveWebSocketChatLimitTest {

    @Mock
    private LiveBroadcastService broadcastService;
    @Mock
    private LiveMessageService liveMessageService;
    @Mock
    private LiveRoomMapper liveRoomMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private WebSocketSession session;

    private LiveWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LiveWebSocketHandler(broadcastService, liveMessageService, liveRoomMapper, stringRedisTemplate);
    }

    private void stubChatSession() {
        Map<String, Object> attributes = Map.of(
                LiveWebSocketInterceptor.ATTR_ROOM_ID, 1001L,
                LiveWebSocketInterceptor.ATTR_USER_ID, 3001L,
                LiveWebSocketInterceptor.ATTR_USER_NAME, "小明",
                LiveWebSocketInterceptor.ATTR_USER_ROLE, LiveSenderRole.STUDENT
        );
        when(session.getAttributes()).thenReturn(attributes);
        // 连接维度频率限制依赖 sessionId；长度超限/控制类消息路径不会使用，声明为 lenient
        lenient().when(session.getId()).thenReturn("session-chat-001");
    }

    private void stubRoomAllowChat() {
        // 仅 CHAT 路径会触发禁言校验，控制类消息不会使用，声明为 lenient
        lenient().when(liveRoomMapper.selectById(1001L)).thenReturn(
                LiveRoomEntity.builder().id(1001L).allowChat(true).build());
    }

    private LiveMessageEntity stubSavedMessage(String content) {
        LiveMessageEntity saved = LiveMessageEntity.builder()
                .id(5001L)
                .roomId(1001L)
                .sessionId(8001L)
                .senderId(3001L)
                .senderName("小明")
                .senderRole(LiveSenderRole.STUDENT)
                .messageType(LiveMessageType.CHAT)
                .content(content)
                .sentAt(LocalDateTime.now())
                .build();
        when(liveMessageService.saveMessage(eq(1001L), eq(3001L), eq("小明"), eq(LiveSenderRole.STUDENT), eq(LiveMessageType.CHAT), eq(content)))
                .thenReturn(saved);
        return saved;
    }

    @Test
    @DisplayName("弹幕超过 200 字符被拒绝并返回 LIVE_CHAT_TOO_LONG，且不落库不广播")
    void testChatTooLong_Rejected() throws Exception {
        stubChatSession();
        stubRoomAllowChat();

        String tooLong = "超".repeat(LiveWebSocketHandler.MAX_MESSAGE_LENGTH + 1);
        TextMessage incoming = new TextMessage("{\"type\":\"CHAT\",\"content\":\"" + tooLong + "\"}");
        handler.handleTextMessage(session, incoming);

        verify(session).sendMessage(argThat(msg -> msg != null
                && msg.getPayload().toString().contains("LIVE_CHAT_TOO_LONG")));
        verifyNoInteractions(liveMessageService);
        verify(broadcastService, never()).broadcastToRoom(any(), any());
    }

    @Test
    @DisplayName("200 字符整的弹幕正常通过")
    void testChatExactlyMaxLength_Accepted() throws Exception {
        stubChatSession();
        stubRoomAllowChat();

        String boundary = "限".repeat(LiveWebSocketHandler.MAX_MESSAGE_LENGTH);
        stubSavedMessage(boundary);

        TextMessage incoming = new TextMessage("{\"type\":\"CHAT\",\"content\":\"" + boundary + "\"}");
        handler.handleTextMessage(session, incoming);

        verify(liveMessageService, times(1))
                .saveMessage(eq(1001L), eq(3001L), eq("小明"), eq(LiveSenderRole.STUDENT), eq(LiveMessageType.CHAT), eq(boundary));
        verify(broadcastService).broadcastToRoom(eq(1001L), argThat(msg -> "CHAT".equals(msg.getType())));
    }

    @Test
    @DisplayName("5 秒内重复发送弹幕被拒绝并返回 LIVE_CHAT_RATE_LIMITED")
    void testChatTooFrequent_SecondMessageRejected() throws Exception {
        stubChatSession();
        stubRoomAllowChat();
        stubSavedMessage("第一条弹幕");

        TextMessage first = new TextMessage("{\"type\":\"CHAT\",\"content\":\"第一条弹幕\"}");
        handler.handleTextMessage(session, first);

        // 模拟第二次发送：校验拦截，saveMessage 不应再被调用
        TextMessage second = new TextMessage("{\"type\":\"CHAT\",\"content\":\"第二条弹幕\"}");
        handler.handleTextMessage(session, second);

        verify(liveMessageService, times(1))
                .saveMessage(eq(1001L), eq(3001L), eq("小明"), eq(LiveSenderRole.STUDENT), eq(LiveMessageType.CHAT), eq("第一条弹幕"));
        verify(session).sendMessage(argThat(msg -> msg != null
                && msg.getPayload().toString().contains("LIVE_CHAT_RATE_LIMITED")));
        verify(broadcastService, times(1)).broadcastToRoom(any(), any());
    }

    @Test
    @DisplayName("不同连接的弹幕互不干扰（各自独立限流）")
    void testChatRateLimit_IsolatedPerSession() throws Exception {
        stubChatSession();
        stubRoomAllowChat();
        stubSavedMessage("A 的弹幕");

        WebSocketSession anotherSession = org.mockito.Mockito.mock(WebSocketSession.class);
        Map<String, Object> anotherAttrs = Map.of(
                LiveWebSocketInterceptor.ATTR_ROOM_ID, 1001L,
                LiveWebSocketInterceptor.ATTR_USER_ID, 3002L,
                LiveWebSocketInterceptor.ATTR_USER_NAME, "小红",
                LiveWebSocketInterceptor.ATTR_USER_ROLE, LiveSenderRole.STUDENT
        );
        when(anotherSession.getAttributes()).thenReturn(anotherAttrs);
        when(anotherSession.getId()).thenReturn("session-chat-002");
        when(liveMessageService.saveMessage(eq(1001L), eq(3002L), eq("小红"), eq(LiveSenderRole.STUDENT), eq(LiveMessageType.CHAT), eq("B 的弹幕")))
                .thenReturn(LiveMessageEntity.builder().id(5002L).roomId(1001L).sessionId(8001L)
                        .senderId(3002L).senderName("小红").senderRole(LiveSenderRole.STUDENT)
                        .messageType(LiveMessageType.CHAT).content("B 的弹幕").sentAt(LocalDateTime.now()).build());

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"CHAT\",\"content\":\"A 的弹幕\"}"));
        handler.handleTextMessage(anotherSession, new TextMessage("{\"type\":\"CHAT\",\"content\":\"B 的弹幕\"}"));

        verify(liveMessageService, times(1))
                .saveMessage(eq(1001L), eq(3001L), eq("小明"), eq(LiveSenderRole.STUDENT), eq(LiveMessageType.CHAT), eq("A 的弹幕"));
        verify(liveMessageService, times(1))
                .saveMessage(eq(1001L), eq(3002L), eq("小红"), eq(LiveSenderRole.STUDENT), eq(LiveMessageType.CHAT), eq("B 的弹幕"));
        verify(session, never()).sendMessage(argThat(msg -> msg != null
                && msg.getPayload().toString().contains("LIVE_CHAT_RATE_LIMITED")));
    }

    @Test
    @DisplayName("控制类消息（LIKE/HAND_UP/PING）不受长度与频率限制影响")
    void testControlMessages_NotRateLimited() throws Exception {
        stubChatSession();
        stubRoomAllowChat();

        // 高频发送 LIKE 与 HAND_UP 控制消息不应被拦截
        for (int i = 0; i < 10; i++) {
            handler.handleTextMessage(session, new TextMessage("{\"type\":\"LIKE\"}"));
        }
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"HAND_UP\",\"payload\":{\"question\":\"q1\"}}"));
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"PING\"}"));

        verify(broadcastService, times(10)).broadcastToRoom(eq(1001L), argThat(msg -> "LIKE".equals(msg.getType())));
        verify(broadcastService, times(1)).broadcastToRoom(eq(1001L), argThat(msg -> "HAND_UP".equals(msg.getType())));
        verify(session).sendMessage(argThat(msg -> msg != null
                && msg.getPayload().toString().contains("\"PONG\"")));
        verifyNoInteractions(liveMessageService);
    }
}
