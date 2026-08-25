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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

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
