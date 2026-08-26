package com.educloud.search.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.search.dto.response.IndexTaskProgressResponse;
import com.educloud.search.security.JwtSecurityUtils;
import com.educloud.search.service.IndexRebuildService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端索引运维与重建 REST 控制器
 * 受权限控制：需具备 'search:rebuild' 权限或拥有 'ADMIN' 角色。
 */
@RestController
@RequestMapping("/api/v1/search/admin")
@RequiredArgsConstructor
@Validated
public class SearchAdminController {

    private final IndexRebuildService indexRebuildService;
    private final ApiResponseFactory responses;

    /**
     * 触发全量索引平滑重建（异步执行）
     * 提取当前操作人（优先从 Security Context 提取 username，无则取 header 或 default 'admin'）
     *
     * @param jwt     当前认证 JWT
     * @param request HTTP 请求
     * @return 初始任务进度对象
     */
    @PostMapping("/rebuild-index")
    @PreAuthorize("hasAuthority('search:rebuild') or hasRole('ADMIN') or hasRole('SYSTEM_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ApiResponse<IndexTaskProgressResponse> rebuildIndex(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        String operator = JwtSecurityUtils.extractOperator(jwt, request);
        IndexTaskProgressResponse progress = indexRebuildService.triggerFullRebuild(operator);
        return responses.success(progress);
    }

    /**
     * 查询指定任务编号的重建进度
     *
     * @param taskNo 任务唯一编号
     * @return 任务进度详情
     */
    @GetMapping("/tasks/{taskNo}")
    @PreAuthorize("hasAuthority('search:rebuild') or hasRole('ADMIN') or hasRole('SYSTEM_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ApiResponse<IndexTaskProgressResponse> getTaskProgress(
            @PathVariable("taskNo") String taskNo) {
        IndexTaskProgressResponse progress = indexRebuildService.getTaskProgress(taskNo);
        return responses.success(progress);
    }

    /**
     * 查询最近触发的索引任务列表
     *
     * @param limit 返回记录数限制（默认 20，最大 100）
     * @return 任务列表（按创建时间倒序排列）
     */
    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('search:rebuild') or hasRole('ADMIN') or hasRole('SYSTEM_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ApiResponse<List<IndexTaskProgressResponse>> listRecentTasks(
            @RequestParam(value = "limit", required = false, defaultValue = "20")
            @Min(value = 1, message = "查询条数最小为 1")
            @Max(value = 100, message = "查询条数最大为 100") Integer limit) {
        int effectiveLimit = (limit != null && limit > 0) ? limit : 20;
        List<IndexTaskProgressResponse> tasks = indexRebuildService.listRecentTasks(effectiveLimit);
        return responses.success(tasks);
    }
}
