package com.educloud.analytics.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.analytics.entity.ActivityFeedEntity;
import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ActivityFeedEntity 字段映射与 ActivityFeedMapper 幂等插入 SQL 单测。
 */
class ActivityFeedEntityTest {

    @Test
    @DisplayName("测试 ActivityFeedEntity 构造与各字段映射")
    void testEntityFieldMapping() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 27, 10, 30, 15);
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 27, 10, 30, 16);
        ActivityFeedEntity entity = ActivityFeedEntity.builder()
                .id(1L)
                .actorId("stu_1001")
                .actorRole("STUDENT")
                .actionType("ENROLLED")
                .targetType("COURSE")
                .targetId("course_101")
                .targetTitle("Spring Cloud 微服务实战")
                .extraJson("{\"source\":\"FREE\"}")
                .sourceEvent("EVT_001_ENROLLED")
                .occurredAt(occurredAt)
                .createdAt(createdAt)
                .build();

        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getActorId()).isEqualTo("stu_1001");
        assertThat(entity.getActorRole()).isEqualTo("STUDENT");
        assertThat(entity.getActionType()).isEqualTo("ENROLLED");
        assertThat(entity.getTargetType()).isEqualTo("COURSE");
        assertThat(entity.getTargetId()).isEqualTo("course_101");
        assertThat(entity.getTargetTitle()).isEqualTo("Spring Cloud 微服务实战");
        assertThat(entity.getExtraJson()).contains("FREE");
        assertThat(entity.getSourceEvent()).isEqualTo("EVT_001_ENROLLED");
        assertThat(entity.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("测试实体 @TableName 与 @TableId 注解")
    void testEntityAnnotations() {
        TableName tableName = ActivityFeedEntity.class.getAnnotation(TableName.class);
        assertThat(tableName).isNotNull();
        assertThat(tableName.value()).isEqualTo("activity_feed");

        TableId tableId = findField("id").getAnnotation(TableId.class);
        assertThat(tableId).isNotNull();
        assertThat(tableId.type()).isEqualTo(IdType.AUTO);
    }

    @Test
    @DisplayName("测试实体字段与表列名映射（下划线命名）")
    void testTableFieldMapping() {
        assertThat(columnOf("actorId")).isEqualTo("actor_id");
        assertThat(columnOf("actorRole")).isEqualTo("actor_role");
        assertThat(columnOf("actionType")).isEqualTo("action_type");
        assertThat(columnOf("targetType")).isEqualTo("target_type");
        assertThat(columnOf("targetId")).isEqualTo("target_id");
        assertThat(columnOf("targetTitle")).isEqualTo("target_title");
        assertThat(columnOf("extraJson")).isEqualTo("extra_json");
        assertThat(columnOf("sourceEvent")).isEqualTo("source_event");
        assertThat(columnOf("occurredAt")).isEqualTo("occurred_at");
        assertThat(columnOf("createdAt")).isEqualTo("created_at");
    }

    @Test
    @DisplayName("测试 ActivityFeedMapper 继承 BaseMapper 且含幂等插入方法")
    void testMapperInterface() throws NoSuchMethodException {
        assertThat(BaseMapper.class).isAssignableFrom(ActivityFeedMapper.class);

        Method insertIdempotent = ActivityFeedMapper.class.getMethod("insertIdempotent", ActivityFeedEntity.class);
        Insert insert = insertIdempotent.getAnnotation(Insert.class);
        assertThat(insert).isNotNull();
        String sql = String.join(" ", insert.value());
        assertThat(sql).contains("INSERT INTO activity_feed");
        assertThat(sql).contains("ON DUPLICATE KEY UPDATE id = id");
        assertThat(sql).contains("#{actorId}").contains("#{sourceEvent}").contains("#{occurredAt}");
    }

    private static Field findField(String name) {
        try {
            return ActivityFeedEntity.class.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new AssertionError("field not found: " + name, e);
        }
    }

    private static String columnOf(String fieldName) {
        TableField tableField = findField(fieldName).getAnnotation(TableField.class);
        assertThat(tableField).as("field %s should have @TableField", fieldName).isNotNull();
        return tableField.value();
    }
}
