package com.educloud.search.service;

import com.educloud.search.dto.request.CourseSearchQuery;
import com.educloud.search.dto.response.CourseSearchResponse;

/**
 * 课程全文检索与多维聚合搜索服务接口
 */
public interface SearchService {

    /**
     * 执行课程全文检索、多维过滤、排序与聚合统计
     *
     * @param query 搜索条件请求对象
     * @return 统一搜索响应（包含课程卡片列表、多维聚合、分页信息及降级标识）
     */
    CourseSearchResponse searchCourses(CourseSearchQuery query);
}
