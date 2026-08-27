package com.educloud.recommendation.mapper;

import com.educloud.recommendation.entity.RecommendationFeedbackEntity;
import com.educloud.recommendation.entity.RecommendationRuleConfigEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 纯 POJO 断言测试：不连接数据库、不使用 @SpringBootTest/Testcontainers/H2。
 * <p>
 * 说明：RecommendationFeedbackMapper.insertOrIgnore 的幂等语义
 * （INSERT ... ON DUPLICATE KEY UPDATE id = id，依赖 uk_feedback 唯一键
 *  user_id + course_id + action，重复提交静默成功）不在本测试覆盖范围内，
 * 由 VM 集成阶段使用真实 MySQL 验证。
 */
class RecommendationEntitiesTest {

    @Test
    @DisplayName("测试 RecommendationRuleConfigEntity 字段完整性")
    void testRecommendationRuleConfigEntity() {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 27, 10, 30, 0);
        RecommendationRuleConfigEntity entity = RecommendationRuleConfigEntity.builder()
                .id(1L)
                .ruleKey("POPULAR")
                .enabled(Boolean.TRUE)
                .weight(40)
                .configVersion(1)
                .updatedAt(updatedAt)
                .build();

        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getRuleKey()).isEqualTo("POPULAR");
        assertThat(entity.getEnabled()).isTrue();
        assertThat(entity.getWeight()).isEqualTo(40);
        assertThat(entity.getConfigVersion()).isEqualTo(1);
        assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("测试 RecommendationFeedbackEntity 字段完整性")
    void testRecommendationFeedbackEntity() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 27, 10, 30, 0);
        RecommendationFeedbackEntity entity = RecommendationFeedbackEntity.builder()
                .id(1L)
                .userId(1001L)
                .courseId(2001L)
                .action("DISLIKE")
                .reason("课程内容与预期不符")
                .createdAt(createdAt)
                .build();

        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getUserId()).isEqualTo(1001L);
        assertThat(entity.getCourseId()).isEqualTo(2001L);
        assertThat(entity.getAction()).isEqualTo("DISLIKE");
        assertThat(entity.getReason()).isEqualTo("课程内容与预期不符");
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    }
}
