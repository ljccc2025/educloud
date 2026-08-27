package com.educloud.live.websocket;

import com.educloud.live.entity.LiveMessageEntity;
import com.educloud.live.entity.LiveRoomEntity;
import com.educloud.live.enums.LiveMessageType;
import com.educloud.live.enums.LiveSenderRole;
import com.educloud.live.mapper.LiveRoomMapper;
import com.educloud.live.service.LiveMessageService;
import com.educloud.live.websocket.model.WebSocketMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiveWebSocketHandler extends TextWebSocketHandler {

    /** 弹幕文本最大长度（字符），超出直接拒绝并返回错误提示 */
    public static final int MAX_MESSAGE_LENGTH = 200;

    /** 弹幕发送最小间隔（毫秒）：每用户每 5 秒最多 1 条弹幕 */
    public static final long MIN_INTERVAL_MS = 5000L;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    /**
     * 弹幕频率限制状态：connectionId/sessionId -> 最近一次弹幕发送时间戳。
     * 内存实现，单实例部署足够；多实例部署时需替换为 Redis（key: live:chat:rate:{userId}）
     * 以保证跨实例限流一致，并注意与 WebSocket 会话生命周期对齐清理。
     */
    private final ConcurrentHashMap<String, Long> lastChatSendAt = new ConcurrentHashMap<>();

    private final LiveBroadcastService broadcastService;
    private final LiveMessageService liveMessageService;
    private final LiveRoomMapper liveRoomMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long roomId = (Long) session.getAttributes().get(LiveWebSocketInterceptor.ATTR_ROOM_ID);
        Long userId = (Long) session.getAttributes().get(LiveWebSocketInterceptor.ATTR_USER_ID);
        String userName = (String) session.getAttributes().get(LiveWebSocketInterceptor.ATTR_USER_NAME);
        LiveSenderRole role = (LiveSenderRole) session.getAttributes().get(LiveWebSocketInterceptor.ATTR_USER_ROLE);

        if (roomId == null || userId == null) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException ignored) {
            }
            return;
        }

        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);

        try {
            stringRedisTemplate.opsForSet().add("educloud:live:room:" + roomId + ":online_users", String.valueOf(userId));
            Long count = stringRedisTemplate.opsForSet().size("educloud:live:room:" + roomId + ":online_users");

            WebSocketMessage<Map<String, Object>> joinMsg = WebSocketMessage.<Map<String, Object>>builder()
                    .type("JOIN_ROOM")
                    .roomId(roomId)
                    .senderId(userId)
                    .senderName(userName)
                    .senderRole(role)
                    .payload(Map.of("onlineCount", count != null ? count : 1, "userId", userId, "userName", userName != null ? userName : ""))
                    .timestamp(System.currentTimeMillis())
                    .build();

            broadcastService.broadcastToRoom(roomId, joinMsg);
        } catch (Exception e) {
            log.error("Error after connection established: roomId={}, userId={}", roomId, userId, e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long roomId = (Long) session.getAttributes().get(LiveWebSocketInterceptor.ATTR_ROOM_ID);
        Long userId = (Long) session.getAttributes().get(LiveWebSocketInterceptor.ATTR_USER_ID);
        String userName = (String) session.getAttributes().get(LiveWebSocketInterceptor.ATTR_USER_NAME);
        LiveSenderRole role = (LiveSenderRole) session.getAttributes().get(LiveWebSocketInterceptor.ATTR_USER_ROLE);

        if (roomId == null || userId == null) {
            return;
        }

        String payload = message.getPayload();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            String type = root.path("type").asText("CHAT").toUpperCase();

            if ("PING".equals(type)) {
                session.sendMessage(new TextMessage("{\"type\":\"PONG\",\"timestamp\":" + System.currentTimeMillis() + "}"));
                return;
            }

            if ("CHAT".equals(type)) {
                String content = root.path("content").asText(root.path("payload").asText(""));
                LiveRoomEntity room = liveRoomMapper.selectById(roomId);
                if (room != null && Boolean.FALSE.equals(room.getAllowChat()) && role == LiveSenderRole.STUDENT) {
                    session.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"payload\":{\"code\":\"LIVE_CHAT_MUTED\",\"message\":\"当前直播间处于全员禁言状态\"}}"));
                    return;
                }

                // 长度限制：超过 200 字符直接拒绝（选择拒绝而非截断：截断会静默篡改用户内容且易造成歧义，
                // 明确拒绝并返回错误提示更透明，与现有禁言拦截的 ERROR 返回风格保持一致）
                if (content.length() > MAX_MESSAGE_LENGTH) {
                    log.warn("Chat rejected: too long, roomId={}, userId={}, length={}", roomId, userId, content.length());
                    session.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"payload\":{\"code\":\"LIVE_CHAT_TOO_LONG\",\"message\":\"弹幕内容超出长度限制（最多" + MAX_MESSAGE_LENGTH + "字符）\"}}"));
                    return;
                }

                // 频率限制：每用户每 5 秒最多 1 条弹幕（按连接维度 sessionId 统计）
                String sessionKey = session.getId() != null ? session.getId() : "user-" + userId;
                long now = System.currentTimeMillis();
                Long lastSendTime = lastChatSendAt.get(sessionKey);
                if (lastSendTime != null && now - lastSendTime < MIN_INTERVAL_MS) {
                    log.warn("Chat rejected: rate limited, roomId={}, userId={}, intervalMs={}",
                            roomId, userId, now - lastSendTime);
                    session.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"payload\":{\"code\":\"LIVE_CHAT_RATE_LIMITED\",\"message\":\"发言过于频繁，请稍后再试\"}}"));
                    return;
                }
                lastChatSendAt.put(sessionKey, now);

                LiveMessageEntity saved = liveMessageService.saveMessage(
                        roomId, userId, userName, role, LiveMessageType.CHAT, content);

                WebSocketMessage<Map<String, Object>> chatMsg = WebSocketMessage.<Map<String, Object>>builder()
                        .type("CHAT")
                        .roomId(roomId)
                        .sessionId(saved.getSessionId())
                        .senderId(userId)
                        .senderName(userName)
                        .senderRole(role)
                        .payload(Map.of("messageId", String.valueOf(saved.getId()), "content", content, "sentAt", saved.getSentAt().toString()))
                        .timestamp(System.currentTimeMillis())
                        .build();

                broadcastService.broadcastToRoom(roomId, chatMsg);
            } else if ("LIKE".equals(type) || "HAND_UP".equals(type) || "WHITEBOARD".equals(type)) {
                JsonNode dataNode = root.has("payload") ? root.get("payload") : root;
                WebSocketMessage<JsonNode> eventMsg = WebSocketMessage.<JsonNode>builder()
                        .type(type)
                        .roomId(roomId)
                        .senderId(userId)
                        .senderName(userName)
                        .senderRole(role)
                        .payload(dataNode)
                        .timestamp(System.currentTimeMillis())
                        .build();

                broadcastService.broadcastToRoom(roomId, eventMsg);
            }
        } catch (Exception e) {
            log.error("Error processing websocket message: roomId={}, userId={}", roomId, userId, e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long roomId = (Long) session.getAttributes().get(LiveWebSocketInterceptor.ATTR_ROOM_ID);
        Long userId = (Long) session.getAttributes().get(LiveWebSocketInterceptor.ATTR_USER_ID);
        String userName = (String) session.getAttributes().get(LiveWebSocketInterceptor.ATTR_USER_NAME);
        LiveSenderRole role = (LiveSenderRole) session.getAttributes().get(LiveWebSocketInterceptor.ATTR_USER_ROLE);

        // 清理频率限制状态，避免连接维度内存泄漏
        if (session.getId() != null) {
            lastChatSendAt.remove(session.getId());
        }

        if (roomId != null) {
            Set<WebSocketSession> sessions = roomSessions.get(roomId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    roomSessions.remove(roomId);
                }
            }

            try {
                if (userId != null) {
                    stringRedisTemplate.opsForSet().remove("educloud:live:room:" + roomId + ":online_users", String.valueOf(userId));
                    Long count = stringRedisTemplate.opsForSet().size("educloud:live:room:" + roomId + ":online_users");

                    WebSocketMessage<Map<String, Object>> leaveMsg = WebSocketMessage.<Map<String, Object>>builder()
                            .type("LEAVE_ROOM")
                            .roomId(roomId)
                            .senderId(userId)
                            .senderName(userName)
                            .senderRole(role)
                            .payload(Map.of("onlineCount", count != null ? count : 0, "userId", userId))
                            .timestamp(System.currentTimeMillis())
                            .build();

                    broadcastService.broadcastToRoom(roomId, leaveMsg);
                }
            } catch (Exception e) {
                log.error("Error on connection closed: roomId={}, userId={}", roomId, userId, e);
            }
        }
    }

    public void sendToLocalRoom(Long roomId, String textJson) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage textMessage = new TextMessage(textJson);
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                try {
                    synchronized (s) {
                        s.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.warn("Failed to send message to session: sessionId={}", s.getId(), e);
                }
            }
        }
    }

    public int getLocalSessionCount(Long roomId) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        return sessions != null ? sessions.size() : 0;
    }
}
