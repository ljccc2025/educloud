package com.educloud.analytics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.analytics.entity.AuditEventReadModelEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventReadModelMapperTest {

    @Test
    @DisplayName("测试 AuditEventReadModelEntity 构造与各字段映射")
    void testAuditEventReadModelEntity() {
        LocalDateTime now = LocalDateTime.now();
        AuditEventReadModelEntity entity = AuditEventReadModelEntity.builder()
                .id(1L)
                .auditId("AUDIT_20260826_001")
                .sourceService("educloud-course")
                .actorId("teacher_01")
                .actorRoles("ROLE_TEACHER")
                .action("COURSE_PUBLISH")
                .resourceType("COURSE")
                .resourceId("course_101")
                .level("INFO")
                .clientIp("192.168.100.45")
                .occurredAt(now)
                .payloadJson("{\"courseId\":\"course_101\",\"title\":\"Vue 3\"}")
                .createdAt(now)
                .build();

        assertThat(entity.getAuditId()).isEqualTo("AUDIT_20260826_001");
        assertThat(entity.getSourceService()).isEqualTo("educloud-course");
        assertThat(entity.getAction()).isEqualTo("COURSE_PUBLISH");
        assertThat(entity.getLevel()).isEqualTo("INFO");
        assertThat(entity.getClientIp()).isEqualTo("192.168.100.45");
        assertThat(entity.getPayloadJson()).contains("Vue 3");
    }

    @Test
    @DisplayName("测试 AuditEventReadModelMapper 继承 BaseMapper")
    void testMapperInterface() {
        assertThat(BaseMapper.class).isAssignableFrom(AuditEventReadModelMapper.class);
    }
}
