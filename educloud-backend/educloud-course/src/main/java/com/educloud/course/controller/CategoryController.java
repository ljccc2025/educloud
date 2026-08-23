package com.educloud.course.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.course.dto.response.CategoryResponse;
import com.educloud.course.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课程分类公开控制器（M05 任务 7）。
 *
 * <p>GET /api/v1/categories 匿名可达：Gateway AccessPolicy.PUBLIC_READ 放行后无
 * Authorization 头转发，Course 服务内 SecurityConfig 同步 permitAll（参照 user
 * /api/v1/platform-config/public 处理）。</p>
 */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final ApiResponseFactory responses;

    public CategoryController(CategoryService categoryService, ApiResponseFactory responses) {
        this.categoryService = categoryService;
        this.responses = responses;
    }

    @GetMapping
    public ApiResponse<List<CategoryResponse>> list() {
        return responses.success(categoryService.visibleTree());
    }
}
