package com.educloud.course.observability;

import com.educloud.common.web.RequestContextAccessor;
import com.educloud.course.entity.AuditEventEntity;
import com.educloud.course.mapper.AuditEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M05 任务 16：AuditWriter 单元测试 —— audit_event 行字段映射与 actor_type
 * VARCHAR(32) 长度兼容（超长角色名截断，空角色回退 USER）。
 */
@ExtendWith(MockitoExtension.class)
class AuditWriterTest {

    private static final Instant NOW = Instant.parse("2026-08-24T02:00:00Z");

    @Mock
    private AuditEventMapper auditEventMapper;
    @Mock
    private RequestContextAccessor requestContext;

    private AuditWriter writer;

    @BeforeEach
    void setUp() {
        lenient().when(requestContext.requestId()).thenReturn("req-0001");
        lenient().when(requestContext.traceId()).thenReturn(java.util.Optional.of("trace-0001"));
        writer = new AuditWriter(
                auditEventMapper, requestContext, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void writePersistsAuditRowWithRoleActorTypeAndRequestContext() {
        writer.write("AUDIT_APPROVED", "course_audit", "401",
                1001L, Set.of("SYSTEM_ADMIN", "COURSE_REVIEWER"), "SUCCESS", null);

        AuditEventEntity entity = capturedEntity();
        assertThat(entity.getAuditId()).isNotBlank();
        assertThat(entity.getActorType()).isEqualTo("COURSE_REVIEWER");
        assertThat(entity.getActorId()).isEqualTo("1001");
        assertThat(entity.getActorRolesJson()).contains("COURSE_REVIEWER", "SYSTEM_ADMIN");
        assertThat(entity.getAction()).isEqualTo("AUDIT_APPROVED");
        assertThat(entity.getResourceType()).isEqualTo("course_audit");
        assertThat(entity.getResourceId()).isEqualTo("401");
        assertThat(entity.getResult()).isEqualTo("SUCCESS");
        assertThat(entity.getReason()).isNull();
        assertThat(entity.getRequestId()).isEqualTo("req-0001");
        assertThat(entity.getTraceId()).isEqualTo("trace-0001");
        assertThat(entity.getOccurredAt()).isEqualTo(NOW);
        assertThat(entity.getRetentionClass()).isEqualTo("standard");
        assertThat(entity.getIp()).isNull();
    }

    @Test
    void writeDefaultsActorTypeToUserWhenRolesEmpty() {
        writer.write("ENROLLMENT_CREATED", "enrollment", "501", 7L, Set.of(), "SUCCESS", null);

        AuditEventEntity entity = capturedEntity();
        assertThat(entity.getActorType()).isEqualTo("USER");
        assertThat(entity.getActorRolesJson()).isNull();
    }

    @Test
    void writePersistsRejectReasonAndFailureResult() {
        writer.write("AUDIT_REJECTED", "course_audit", "402", 2002L,
                Set.of("COURSE_REVIEWER"), "SUCCESS", "内容不完整，请补充大纲");

        AuditEventEntity entity = capturedEntity();
        assertThat(entity.getAction()).isEqualTo("AUDIT_REJECTED");
        assertThat(entity.getReason()).isEqualTo("内容不完整，请补充大纲");
        assertThat(entity.getResult()).isEqualTo("SUCCESS");
    }

    @Test
    void actorTypeIsCompatibleWithVarchar32Column() {
        String longRole = "EXTREMELY_LONG_ROLE_NAME_EXCEEDING_THIRTY_TWO_CHARACTERS";
        assertThat(longRole.length()).isGreaterThan(32);
        assertThat(AuditWriter.safeActorType(longRole)).hasSize(32);
        assertThat(AuditWriter.actorType(Set.of("STUDENT"))).isEqualTo("STUDENT");
        assertThat(AuditWriter.actorType(null)).isEqualTo("USER");
        assertThat(AuditWriter.actorType(Set.of())).isEqualTo("USER");
    }

    private AuditEventEntity capturedEntity() {
        ArgumentCaptor<AuditEventEntity> captor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(auditEventMapper).insert(captor.capture());
        return captor.getValue();
    }
}
