package com.educloud.search.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.search.dto.request.CourseSearchQuery;
import com.educloud.search.dto.response.CourseSearchResponse;
import com.educloud.search.dto.response.SuggestResponse;
import com.educloud.search.service.SearchService;
import com.educloud.search.service.SuggestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开端搜索与建议 REST 控制器
 * 提供课程全文检索、多维聚合筛选与实时搜索建议补全能力（白名单完全公开）。
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Validated
public class SearchController {

    private final SearchService searchService;
    private final SuggestService suggestService;
    private final ApiResponseFactory responses;

    /**
     * 课程全文检索、多维过滤、排序与聚合统计
     *
     * @param query 搜索条件参数
     * @return 统一搜索响应（包含课程卡片列表、多维聚合、分页信息及降级标识）
     */
    @GetMapping("/courses")
    public ApiResponse<CourseSearchResponse> searchCourses(@Valid CourseSearchQuery query) {
        CourseSearchResponse response = searchService.searchCourses(query);
        return responses.success(response);
    }

    /**
     * 搜索框实时智能建议与前缀自动补全
     *
     * @param q     输入前缀关键词
     * @param limit 最大建议条数（默认 8）
     * @return 建议项列表响应
     */
    @GetMapping("/suggest")
    public ApiResponse<SuggestResponse> suggest(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "limit", required = false, defaultValue = "8") Integer limit) {
        SuggestResponse response = suggestService.suggest(q, limit);
        return responses.success(response);
    }
}
