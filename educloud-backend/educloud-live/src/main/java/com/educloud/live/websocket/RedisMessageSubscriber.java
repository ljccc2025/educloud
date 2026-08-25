package com.educloud.live.websocket;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessageSubscriber implements MessageListener {

    private final RedisMessageListenerContainer listenerContainer;
    private final LiveWebSocketHandler webSocketHandler;

    @PostConstruct
    public void registerSubscriber() {
        listenerContainer.addMessageListener(this, new PatternTopic(LiveBroadcastService.CHANNEL_PREFIX + "*"));
        log.info("Registered Redis Pub/Sub subscriber for topic: {}*", LiveBroadcastService.CHANNEL_PREFIX);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String body = new String(message.getBody(), StandardCharsets.UTF_8);

            if (channel.startsWith(LiveBroadcastService.CHANNEL_PREFIX)) {
                String roomIdStr = channel.substring(LiveBroadcastService.CHANNEL_PREFIX.length());
                Long roomId = Long.parseLong(roomIdStr);
                webSocketHandler.sendToLocalRoom(roomId, body);
            }
        } catch (Exception e) {
            log.error("Error processing Redis pub/sub message", e);
        }
    }
}
