package com.educloud.course.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.course.security.CoursePermissions;
import com.educloud.course.security.JwtSecurityUtils;
import com.educloud.course.service.CourseService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课程生命周期控制器（M05 任务 10）：下架/重上架/归档 3 端点。
 *
 * <p>依据：规格 §6 —— POST /courses/{id}/offline（course:offline，PUBLISHED→OFFLINE）、
 * POST /courses/{id}/republish（course:republish，OFFLINE→PUBLISHED）、
 * POST /courses/{id}/archive（course:archive，OFFLINE→ARCHIVED，PUBLISHED 必须先下架）。
 * 权限码由 @PreAuthorize 方法级校验；归属校验（规格 §9：course_teacher）在服务层
 * TeacherAccessGuard 完成；当前用户由 JWT subject（userId）解析（JwtSecurityUtils）。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class CourseLifecycleController {

    private final CourseService courseService;
    private final ApiResponseFactory responses;

    public CourseLifecycleController(CourseService courseService, ApiResponseFactory responses) {
        this.courseService = courseService;
        this.responses = responses;
    }

    @PostMapping("/courses/{courseId}/offline")
    @PreAuthorize("hasAuthority('" + CoursePermissions.OFFLINE + "')")
    public ApiResponse<Void> offline(
            @PathVariable Long courseId,
            @AuthenticationPrincipal Jwt jwt) {
        courseService.offline(courseId, JwtSecurityUtils.userId(jwt), JwtSecurityUtils.roles(jwt));
        return responses.success(null);
    }

    @PostMapping("/courses/{courseId}/republish")
    @PreAuthorize("hasAuthority('" + CoursePermissions.REPUBLISH + "')")
    public ApiResponse<Void> republish(
            @PathVariable Long courseId,
            @AuthenticationPrincipal Jwt jwt) {
        courseService.republish(courseId, JwtSecurityUtils.userId(jwt), JwtSecurityUtils.roles(jwt));
        return responses.success(null);
    }

    @PostMapping("/courses/{courseId}/archive")
    @PreAuthorize("hasAuthority('" + CoursePermissions.ARCHIVE + "')")
    public ApiResponse<Void> archive(
            @PathVariable Long courseId,
            @AuthenticationPrincipal Jwt jwt) {
        courseService.archive(courseId, JwtSecurityUtils.userId(jwt), JwtSecurityUtils.roles(jwt));
        return responses.success(null);
    }
}
