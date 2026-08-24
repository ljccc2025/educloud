package com.educloud.content.messaging;

import com.educloud.common.web.RequestContextAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ContentEventPublisher {

    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final RequestContextAccessor requestContextAccessor;

    public void contentRevisionPublished(
            Long courseId,
            Long contentRootId,
            Long publishedRevisionId,
            Integer revisionNo,
            long aggregateVersion,
            LocalDateTime publishedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", courseId);
        payload.put("contentRootId", contentRootId);
        payload.put("publishedRevisionId", publishedRevisionId);
        payload.put("revisionNo", revisionNo);
        payload.put("aggregateVersion", aggregateVersion);
        payload.put("publishedAt", publishedAt.toString());

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize ContentRevisionPublished payload", e);
        }

        outboxWriter.write(
                "CourseContent",
                String.valueOf(contentRootId),
                "ContentRevisionPublished",
                1,
                aggregateVersion,
                payloadJson,
                requestContextAccessor.requestId(),
                requestContextAccessor.traceId().orElse(null));
    }
}
