package com.educloud.course.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.course.dto.response.AdminCourseResponse;
import com.educloud.course.security.CoursePermissions;
import com.educloud.course.security.JwtSecurityUtils;
import com.educloud.course.service.CourseService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端课程管理控制器（M05 任务 23）：GET /api/v1/admin/courses 全状态分页。
 *
 * <p>管理查询缺口补齐：公开目录 GET /courses 是匿名 PUBLIC_READ（仅 PUBLISHED，
 * CourseListQuery 无 status 参数，网关 PUBLIC_READ 放行后不能承载管理全状态查询），
 * 故按任务 22 教师列表先例新增管理专用端点（course:audit）。生命周期操作
 * （offline/republish/archive）复用 CourseLifecycleController；管理角色经 V004 挂全部
 * course:* 权限码，服务内 TeacherAccessGuard 对 SYSTEM_ADMIN/SUPER_ADMIN 放行归属校验。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class CourseAdminController {

    private final CourseService courseService;
    private final ApiResponseFactory responses;

    public CourseAdminController(CourseService courseService, ApiResponseFactory responses) {
        this.courseService = courseService;
        this.responses = responses;
    }

    @GetMapping("/admin/courses")
    @PreAuthorize("hasAuthority('" + CoursePermissions.AUDIT + "')")
    public ApiResponse<PageResponse<AdminCourseResponse>> listCourses(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String lifecycleStatus,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(courseService.listAdminCourses(
                JwtSecurityUtils.userId(jwt), page, pageSize, lifecycleStatus));
    }
}
