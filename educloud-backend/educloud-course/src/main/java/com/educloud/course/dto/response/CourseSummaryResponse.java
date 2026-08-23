package com.educloud.course.dto.response;

import java.math.BigDecimal;

/**
 * 课程公开列表项（M05 任务 11）：GET /api/v1/courses 分页响应元素。
 *
 * <p>Snowflake id 为 String（M04 坑 1）；price 金额字符串化（十进制金额，任务 8
 * CourseDraftResponse 同一风格，任务 11 公开列表沿用）。coverUrl 本任务恒为 null
 * 占位（任务 12 File grant 后填充）；teacherName 当前以 teacherId 字符串占位（M05
 * 无 user 服务 Profile 客户端，展示名解析留给后续接入）；enrolled 需登录态
 * （无 token 时 false）。</p>
 */
public record CourseSummaryResponse(
        String id,
        String title,
        String coverUrl,
        String teacherName,
        String categoryName,
        String level,
        String price,
        BigDecimal ratingAvg,
        Integer ratingCount,
        Integer enrollmentCount,
        boolean enrolled) {
}
