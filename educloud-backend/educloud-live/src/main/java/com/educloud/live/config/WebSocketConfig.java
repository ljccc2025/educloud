package com.educloud.live.config;

import com.educloud.live.websocket.LiveWebSocketHandler;
import com.educloud.live.websocket.LiveWebSocketInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final LiveWebSocketHandler liveWebSocketHandler;
    private final LiveWebSocketInterceptor liveWebSocketInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(liveWebSocketHandler, "/ws/v1/live/*")
                .addInterceptors(liveWebSocketInterceptor)
                .setAllowedOrigins("*");
    }
}
