package com.educloud.analytics.messaging;

import com.educloud.analytics.entity.AuditEventReadModelEntity;
import com.educloud.analytics.messaging.event.AuditEvent;
import com.educloud.analytics.service.AuditEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

    @Mock
    private AuditEventService auditEventService;

    @InjectMocks
    private AuditEventConsumer consumer;

    @Test
    @DisplayName("测试审计事件消费并正确组装 ReadModel 实体")
    void testOnAuditEvent() {
        LocalDateTime now = LocalDateTime.now();
        AuditEvent event = AuditEvent.builder()
                .auditId("AUDIT_1001")
                .sourceService("educloud-course")
                .actorId("teacher_01")
                .actorRoles("ROLE_TEACHER")
                .action("COURSE_PUBLISH")
                .resourceType("COURSE")
                .resourceId("course_501")
                .level("INFO")
                .clientIp("192.168.1.100")
                .occurredAt(now)
                .payloadJson("{\"title\":\"Vue 3 实战\"}")
                .build();

        consumer.onAuditEvent(event);

        ArgumentCaptor<AuditEventReadModelEntity> captor = ArgumentCaptor.forClass(AuditEventReadModelEntity.class);
        verify(auditEventService, times(1)).recordAuditEvent(captor.capture());

        AuditEventReadModelEntity captured = captor.getValue();
        assertThat(captured.getAuditId()).isEqualTo("AUDIT_1001");
        assertThat(captured.getSourceService()).isEqualTo("educloud-course");
        assertThat(captured.getActorId()).isEqualTo("teacher_01");
        assertThat(captured.getAction()).isEqualTo("COURSE_PUBLISH");
        assertThat(captured.getResourceId()).isEqualTo("course_501");
        assertThat(captured.getLevel()).isEqualTo("INFO");
        assertThat(captured.getClientIp()).isEqualTo("192.168.1.100");
    }
}
