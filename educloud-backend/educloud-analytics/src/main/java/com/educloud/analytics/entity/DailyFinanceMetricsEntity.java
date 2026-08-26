package com.educloud.analytics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("daily_finance_metrics")
public class DailyFinanceMetricsEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("metric_date")
    private LocalDate metricDate;

    @TableField("gross_revenue_cents")
    private Long grossRevenueCents;

    @TableField("refund_amount_cents")
    private Long refundAmountCents;

    @TableField("net_revenue_cents")
    private Long netRevenueCents;

    @TableField("order_count")
    private Integer orderCount;

    @TableField("refund_count")
    private Integer refundCount;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
