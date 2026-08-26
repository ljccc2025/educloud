package com.educloud.analytics.messaging;

import com.educloud.analytics.config.RabbitMqConfig;
import com.educloud.analytics.entity.AuditEventReadModelEntity;
import com.educloud.analytics.messaging.event.AuditEvent;
import com.educloud.analytics.service.AuditEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventConsumer {

    private final AuditEventService auditEventService;

    @RabbitListener(queues = RabbitMqConfig.QUEUE_ANALYTICS_AUDIT)
    public void onAuditEvent(AuditEvent event) {
        log.info("Received AuditEvent: auditId={}, action={}, actor={}", 
                event.getAuditId(), event.getAction(), event.getActorId());
        if (event == null) {
            return;
        }

        AuditEventReadModelEntity entity = AuditEventReadModelEntity.builder()
                .auditId(event.getAuditId() != null ? event.getAuditId() : "AUDIT_" + System.currentTimeMillis())
                .sourceService(event.getSourceService() != null ? event.getSourceService() : "educloud-unknown")
                .actorId(event.getActorId() != null ? event.getActorId() : "anonymous")
                .actorRoles(event.getActorRoles())
                .action(event.getAction() != null ? event.getAction() : "ACTION")
                .resourceType(event.getResourceType() != null ? event.getResourceType() : "RESOURCE")
                .resourceId(event.getResourceId())
                .level(event.getLevel() != null ? event.getLevel() : "INFO")
                .clientIp(event.getClientIp() != null ? event.getClientIp() : "127.0.0.1")
                .occurredAt(event.getOccurredAt() != null ? event.getOccurredAt() : LocalDateTime.now())
                .payloadJson(event.getPayloadJson())
                .createdAt(LocalDateTime.now())
                .build();

        auditEventService.recordAuditEvent(entity);
    }
}
