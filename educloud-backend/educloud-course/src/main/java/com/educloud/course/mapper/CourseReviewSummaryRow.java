package com.educloud.course.mapper;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 评价评分汇总投影行（CourseReviewMapper.selectVisibleSummary 的聚合结果，
 * M05 任务 14）：AVG(rating)/COUNT(*) 仅统计 VISIBLE 评价（HIDDEN 不计入）。
 * MyBatis map-underscore-to-camel-case 自动映射。
 */
@Data
public class CourseReviewSummaryRow {

    /** 无 VISIBLE 评价时 AVG 返回 NULL（服务层归一化为 0.00）。 */
    private BigDecimal ratingAvg;

    private Long ratingCount;
}
