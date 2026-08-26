package com.educloud.analytics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.analytics.entity.DailyPlatformMetricsEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface DailyPlatformMetricsMapper extends BaseMapper<DailyPlatformMetricsEntity> {

    int upsertIncrement(
            @Param("metricDate") LocalDate metricDate,
            @Param("newUsers") int newUsers,
            @Param("newCourses") int newCourses,
            @Param("orders") int orders,
            @Param("gmvCents") long gmvCents,
            @Param("refundCents") long refundCents
    );

    @Select("""
        SELECT 
            metric_date,
            total_users,
            total_courses,
            total_orders,
            gmv_cents,
            refund_cents
        FROM daily_platform_metrics
        WHERE metric_date >= #{startDate}
          AND metric_date <= #{endDate}
        ORDER BY metric_date ASC
    """)
    List<DailyPlatformMetricsEntity> selectDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Select("""
        SELECT 
            COALESCE(MAX(total_users), 0) AS total_users,
            COALESCE(MAX(total_courses), 0) AS total_courses,
            COALESCE(SUM(gmv_cents), 0) AS total_gmv_cents,
            COALESCE(SUM(refund_cents), 0) AS total_refund_cents,
            COALESCE(SUM(total_orders), 0) AS total_orders
        FROM daily_platform_metrics
    """)
    Map<String, Object> selectPlatformSummary();
}
