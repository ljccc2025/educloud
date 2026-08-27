package com.educloud.analytics.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.educloud.analytics.entity.ActivityFeedEntity;
import com.educloud.analytics.mapper.ActivityFeedMapper;
import com.educloud.analytics.service.impl.ActivityFeedServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ActivityFeedServiceImpl 单测：动态写入（extra→JSON、幂等、容错）与按角色查询。
 */
@ExtendWith(MockitoExtension.class)
class ActivityFeedServiceTest {

    @Mock
    private ActivityFeedMapper activityFeedMapper;

    private ActivityFeedServiceImpl service;

    @BeforeEach
    void setUp() {
        // 无 Spring 容器时手动初始化 MyBatis-Plus TableInfo（LambdaQueryWrapper 列名解析依赖）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ActivityFeedEntity.class);
        service = new ActivityFeedServiceImpl(activityFeedMapper, new ObjectMapper());
    }

    @Test
    @DisplayName("记录动态：字段落实体，extra 序列化为 JSON 字符串")
    void testRecordActivityMapsEntityAndSerializesExtra() {
        when(activityFeedMapper.insertIdempotent(any())).thenReturn(1);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 27, 10, 0);

        service.recordActivity("stu_1001", "STUDENT", "ASSIGNMENT_GRADED", "ASSIGNMENT",
                "asg_3001", "微服务模块打包", Map.of("score", 95), "EVT_1_ASSIGNMENT_GRADED", occurredAt);

        ArgumentCaptor<ActivityFeedEntity> captor = ArgumentCaptor.forClass(ActivityFeedEntity.class);
        verify(activityFeedMapper).insertIdempotent(captor.capture());
        ActivityFeedEntity entity = captor.getValue();
        assertThat(entity.getActorId()).isEqualTo("stu_1001");
        assertThat(entity.getActorRole()).isEqualTo("STUDENT");
        assertThat(entity.getActionType()).isEqualTo("ASSIGNMENT_GRADED");
        assertThat(entity.getTargetType()).isEqualTo("ASSIGNMENT");
        assertThat(entity.getTargetId()).isEqualTo("asg_3001");
        assertThat(entity.getTargetTitle()).isEqualTo("微服务模块打包");
        assertThat(entity.getExtraJson()).contains("\"score\":95");
        assertThat(entity.getSourceEvent()).isEqualTo("EVT_1_ASSIGNMENT_GRADED");
        assertThat(entity.getOccurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("幂等：insertIdempotent 返回 0（source_event 已存在）仅记日志不抛异常")
    void testRecordActivityDuplicateIgnored() {
        when(activityFeedMapper.insertIdempotent(any())).thenReturn(0);

        assertThatCode(() -> service.recordActivity("stu_1001", "STUDENT", "ENROLLED", "COURSE",
                "course_101", "Spring Cloud", null, "EVT_DUP", LocalDateTime.now()))
                .doesNotThrowAnyException();

        verify(activityFeedMapper).insertIdempotent(any());
    }

    @Test
    @DisplayName("容错：Mapper 写入异常仅记日志不抛出（不阻断事件消费）")
    void testRecordActivityMapperExceptionSwallowed() {
        when(activityFeedMapper.insertIdempotent(any())).thenThrow(new RuntimeException("DB down"));

        assertThatCode(() -> service.recordActivity("stu_1001", "STUDENT", "ENROLLED", "COURSE",
                "course_101", null, null, "EVT_ERR", null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("actorId/actionType 为空 → 跳过写入")
    void testRecordActivityBlankFieldsSkipped() {
        service.recordActivity(null, "STUDENT", "ENROLLED", null, null, null, null, null, null);
        service.recordActivity(" ", "STUDENT", "ENROLLED", null, null, null, null, null, null);
        service.recordActivity("stu_1001", "STUDENT", "", null, null, null, null, null, null);

        verifyNoInteractions(activityFeedMapper);
    }

    @Test
    @DisplayName("查询动态：limit 钳制为 [1, 50]")
    @SuppressWarnings("unchecked")
    void testListActivitiesClampsLimit() {
        when(activityFeedMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(ActivityFeedEntity.builder().id(1L).build()));

        service.listActivities("stu_1001", "STUDENT", 999);
        ArgumentCaptor<LambdaQueryWrapper<ActivityFeedEntity>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(activityFeedMapper).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("LIMIT 50");
    }

    @Test
    @DisplayName("查询动态：按角色与用户过滤（SQL 含 actor_id/actor_role 条件与时间倒序）")
    @SuppressWarnings("unchecked")
    void testListActivitiesFiltersByActorAndRole() {
        when(activityFeedMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<ActivityFeedEntity> result = service.listActivities("teacher_01", "TEACHER", 10);

        assertThat(result).isEmpty();
        ArgumentCaptor<LambdaQueryWrapper<ActivityFeedEntity>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(activityFeedMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("actor_id").contains("actor_role").contains("ORDER BY");
    }

    @Test
    @DisplayName("查询降级：Mapper 异常返回空数组")
    @SuppressWarnings("unchecked")
    void testListActivitiesDegradesToEmptyOnFailure() {
        when(activityFeedMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenThrow(new RuntimeException("DB down"));

        assertThat(service.listActivities("stu_1001", "STUDENT", 10)).isEmpty();
    }

    @Test
    @DisplayName("查询：空用户/角色直接返回空数组，不查库")
    void testListActivitiesBlankActorReturnsEmpty() {
        assertThat(service.listActivities(null, "STUDENT", 10)).isEmpty();
        assertThat(service.listActivities("stu_1001", "", 10)).isEmpty();
        verify(activityFeedMapper, never()).selectList(any());
    }
}
