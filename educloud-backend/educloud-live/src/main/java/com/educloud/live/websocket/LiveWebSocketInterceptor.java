package com.educloud.live.websocket;

import com.educloud.live.service.LiveTicketService;
import com.educloud.live.websocket.model.LiveTicketPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiveWebSocketInterceptor implements HandshakeInterceptor {

    public static final String ATTR_ROOM_ID = "ROOM_ID";
    public static final String ATTR_USER_ID = "USER_ID";
    public static final String ATTR_USER_NAME = "USER_NAME";
    public static final String ATTR_USER_ROLE = "USER_ROLE";

    private final LiveTicketService liveTicketService;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        URI uri = request.getURI();
        String path = uri.getPath();
        String query = uri.getQuery();

        Long roomId = extractRoomIdFromPath(path);
        if (roomId == null) {
            log.warn("WebSocket handshake failed: invalid path {}", path);
            return false;
        }

        String ticket = extractTicketFromQuery(query);
        if (ticket == null || ticket.isBlank()) {
            log.warn("WebSocket handshake failed: missing ticket for roomId {}", roomId);
            return false;
        }

        try {
            LiveTicketPayload payload = liveTicketService.verifyAndConsumeTicket(roomId, ticket);
            attributes.put(ATTR_ROOM_ID, payload.getRoomId());
            attributes.put(ATTR_USER_ID, payload.getUserId());
            attributes.put(ATTR_USER_NAME, payload.getNickname());
            attributes.put(ATTR_USER_ROLE, payload.getRole());
            return true;
        } catch (Exception e) {
            log.warn("WebSocket handshake rejected: roomId={}, ticket={}, reason={}", roomId, ticket, e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
    }

    private Long extractRoomIdFromPath(String path) {
        if (path == null) {
            return null;
        }
        // pattern: /ws/v1/live/{roomId}
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("live".equalsIgnoreCase(parts[i]) && i + 1 < parts.length) {
                try {
                    return Long.parseLong(parts[i + 1]);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private String extractTicketFromQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0 && "ticket".equalsIgnoreCase(pair.substring(0, idx))) {
                return pair.substring(idx + 1);
            }
        }
        return null;
    }
}
