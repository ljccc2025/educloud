package com.educloud.live.websocket;

import com.educloud.live.websocket.model.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveBroadcastService {

    public static final String CHANNEL_PREFIX = "educloud:live:channel:";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final StringRedisTemplate stringRedisTemplate;

    public void broadcastToRoom(Long roomId, WebSocketMessage<?> message) {
        if (roomId == null || message == null) {
            return;
        }
        try {
            message.setRoomId(roomId);
            if (message.getTimestamp() == 0) {
                message.setTimestamp(System.currentTimeMillis());
            }
            String json = OBJECT_MAPPER.writeValueAsString(message);
            String channel = CHANNEL_PREFIX + roomId;
            stringRedisTemplate.convertAndSend(channel, json);
        } catch (Exception e) {
            log.error("Failed to broadcast message to redis channel: roomId={}", roomId, e);
        }
    }
}
