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
@TableName("daily_platform_metrics")
public class DailyPlatformMetricsEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("metric_date")
    private LocalDate metricDate;

    @TableField("total_users")
    private Integer totalUsers;

    @TableField("new_users")
    private Integer newUsers;

    @TableField("total_courses")
    private Integer totalCourses;

    @TableField("new_courses")
    private Integer newCourses;

    @TableField("total_orders")
    private Integer totalOrders;

    @TableField("gmv_cents")
    private Long gmvCents;

    @TableField("refund_cents")
    private Long refundCents;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
