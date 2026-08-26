package com.educloud.analytics.service;

import com.educloud.analytics.entity.AnalyticsRebuildTaskEntity;
import com.educloud.analytics.enums.RebuildStage;
import com.educloud.analytics.enums.RebuildStatus;
import com.educloud.analytics.mapper.AnalyticsRebuildTaskMapper;
import com.educloud.analytics.service.impl.AggregationRebuildServiceImpl;
import com.educloud.analytics.support.CrossDbBatchExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AggregationRebuildServiceTest {

    @Mock
    private AnalyticsRebuildTaskMapper taskMapper;

    @Mock
    private CrossDbBatchExtractor batchExtractor;

    @Mock
    private DailyAggregationService dailyAggregationService;

    @InjectMocks
    private AggregationRebuildServiceImpl rebuildService;

    @Test
    @DisplayName("测试触发全量指标重算并生成唯一任务号")
    void testTriggerRebuild() {
        String taskNo = rebuildService.triggerRebuild("admin");

        assertThat(taskNo).startsWith("REBUILD_");
        verify(taskMapper, atLeastOnce()).insert(any(AnalyticsRebuildTaskEntity.class));
    }

    @Test
    @DisplayName("测试全量重算同步阶段流转与各领域抽取处理")
    void testExecuteRebuildWorkflow() {
        AnalyticsRebuildTaskEntity task = AnalyticsRebuildTaskEntity.builder()
                .id(1L)
                .taskNo("REBUILD_TEST_001")
                .status(RebuildStatus.RUNNING)
                .stage(RebuildStage.INITIALIZING)
                .build();

        when(batchExtractor.extractUserFacts(0, 1000)).thenReturn(List.of(
                CrossDbBatchExtractor.UserFact.builder().userId(101L).registerDate(LocalDate.now()).build()
        ));
        when(batchExtractor.extractCourseFacts(0, 1000)).thenReturn(List.of(
                CrossDbBatchExtractor.CourseFact.builder().courseId("c_1").title("Java").teacherId("t_1").publishDate(LocalDate.now()).build()
        ));
        when(batchExtractor.extractOrderFacts(0, 1000)).thenReturn(List.of(
                CrossDbBatchExtractor.OrderFact.builder().orderNo("ORD_1").courseId("c_1").teacherId("t_1").amountCents(10000L).status("PAID").orderDate(LocalDate.now()).build()
        ));

        rebuildService.executeRebuild(task);

        assertThat(task.getStatus()).isEqualTo(RebuildStatus.SUCCESS);
        assertThat(task.getStage()).isEqualTo(RebuildStage.COMPLETED);
        verify(dailyAggregationService, times(1)).recordUserRegistered(any());
        verify(dailyAggregationService, times(1)).recordCoursePublished(any(), any(), any(), any());
        verify(dailyAggregationService, times(1)).recordEnrollment(any(), any(), any(), anyLong(), any());
    }
}
