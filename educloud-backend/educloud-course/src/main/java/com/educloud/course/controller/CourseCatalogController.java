package com.educloud.course.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.course.dto.request.CourseListQuery;
import com.educloud.course.dto.response.CourseDetailResponse;
import com.educloud.course.dto.response.CourseSummaryResponse;
import com.educloud.course.security.JwtSecurityUtils;
import com.educloud.course.service.CourseCatalogService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课程公开目录控制器（M05 任务 11）：GET /api/v1/courses 列表 + GET /api/v1/courses/{id} 详情。
 *
 * <p>依据：规格 §6 —— 两端点匿名可达（Gateway AccessPolicy.PUBLIC_READ 放行后无
 * Authorization 头转发，服务内 SecurityConfig 同步 permitAll，仅 GET）；可选登录态：
 * JWT 存在时解析 subject 为当前 userId（enrolled 标记/教师 OWNER 视角），无 token
 * 时传 null（enrolled=false、非 PUBLISHED 一律 404）。排序白名单/分页归一化在
 * CourseCatalogService 完成（非法 sort/priceRange → 400 VALIDATION_FAILED）。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class CourseCatalogController {

    private final CourseCatalogService catalogService;
    private final ApiResponseFactory responses;

    public CourseCatalogController(CourseCatalogService catalogService, ApiResponseFactory responses) {
        this.catalogService = catalogService;
        this.responses = responses;
    }

    @GetMapping("/courses")
    public ApiResponse<PageResponse<CourseSummaryResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String priceRange,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        CourseListQuery query = new CourseListQuery(
                keyword, categoryId, level, priceRange, sort, page, size);
        return responses.success(catalogService.list(query, currentUserId(jwt)));
    }

    @GetMapping("/courses/{courseId}")
    public ApiResponse<CourseDetailResponse> detail(
            @PathVariable Long courseId,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(catalogService.detail(courseId, currentUserId(jwt)));
    }

    private static Long currentUserId(Jwt jwt) {
        return jwt == null ? null : JwtSecurityUtils.userId(jwt);
    }
}
