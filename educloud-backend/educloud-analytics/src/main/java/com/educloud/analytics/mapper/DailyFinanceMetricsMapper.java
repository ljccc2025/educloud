package com.educloud.analytics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.analytics.entity.DailyFinanceMetricsEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface DailyFinanceMetricsMapper extends BaseMapper<DailyFinanceMetricsEntity> {

    int upsertIncrement(
            @Param("metricDate") LocalDate metricDate,
            @Param("grossRevenueCents") long grossRevenueCents,
            @Param("refundAmountCents") long refundAmountCents,
            @Param("netRevenueCents") long netRevenueCents,
            @Param("orderCount") int orderCount,
            @Param("refundCount") int refundCount
    );

    @Select("""
        SELECT 
            DATE_FORMAT(metric_date, '%Y-%m') AS month,
            COALESCE(SUM(gross_revenue_cents), 0) AS total_gross_cents,
            COALESCE(SUM(refund_amount_cents), 0) AS total_refund_cents,
            COALESCE(SUM(net_revenue_cents), 0) AS total_net_cents,
            COALESCE(SUM(order_count), 0) AS total_orders,
            COALESCE(SUM(refund_count), 0) AS total_refunds
        FROM daily_finance_metrics
        WHERE metric_date >= #{startDate}
          AND metric_date <= #{endDate}
        GROUP BY DATE_FORMAT(metric_date, '%Y-%m')
        ORDER BY month ASC
    """)
    List<Map<String, Object>> selectMonthlyFinance(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Select("""
        SELECT 
            COALESCE(SUM(gross_revenue_cents), 0) AS total_gross_cents,
            COALESCE(SUM(refund_amount_cents), 0) AS total_refund_cents,
            COALESCE(SUM(net_revenue_cents), 0) AS total_net_cents,
            COALESCE(SUM(order_count), 0) AS total_orders,
            COALESCE(SUM(refund_count), 0) AS total_refunds
        FROM daily_finance_metrics
    """)
    Map<String, Object> selectFinanceSummary();
}
