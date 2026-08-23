package com.educloud.course.dto.request;

/**
 * 课程公开列表查询（GET /api/v1/courses，M05 任务 11）。
 *
 * <p>查询参数契约（规格 §6）：keyword/categoryId/level/priceRange/sort/page/size。
 * priceRange 枚举 free/under200/200to400/above400；sort 白名单
 * popular/newest/price-asc/price-desc/rating（白名单外 → 400 VALIDATION_FAILED）；
 * 分页默认 page=1 size=20、size 上限 100（服务层归一化）。categoryId 为 Snowflake ID
 * 字符串（DTO 一律 String，M04 坑 1：前端禁止 Number()）。</p>
 */
public record CourseListQuery(
        String keyword,
        String categoryId,
        String level,
        String priceRange,
        String sort,
        Integer page,
        Integer size) {
}
