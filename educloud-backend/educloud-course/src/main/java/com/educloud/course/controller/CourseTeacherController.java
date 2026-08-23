package com.educloud.course.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.course.dto.request.CourseCreateRequest;
import com.educloud.course.dto.request.CourseDraftUpdateRequest;
import com.educloud.course.dto.response.CourseDraftResponse;
import com.educloud.course.dto.response.TeacherCourseResponse;
import com.educloud.course.security.CoursePermissions;
import com.educloud.course.security.JwtSecurityUtils;
import com.educloud.course.service.CourseService;
import com.educloud.course.service.CourseVersionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教师课程控制器（M05 任务 8/22）：建课、草稿管理与教师课程列表。
 *
 * <p>依据：设计规格 §6/§7 —— POST /courses（course:create）建根+首版 DRAFT；
 * GET /teacher/courses（M05 任务 22 补齐，course:update）返回归属教师的全部课程含
 * 生命周期/版本状态（教师课程管理列表必需）；GET /teacher/courses/{id}/draft、
 * POST /courses/{id}/drafts、PUT /course-drafts/{versionId} 均需 course:update，
 * 归属校验（OWNER/CO_TEACHER）在服务层由 TeacherAccessGuard 完成；当前用户由 JWT
 * subject（userId）解析（JwtSecurityUtils）。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class CourseTeacherController {

    private final CourseService courseService;
    private final CourseVersionService courseVersionService;
    private final ApiResponseFactory responses;

    public CourseTeacherController(
            CourseService courseService,
            CourseVersionService courseVersionService,
            ApiResponseFactory responses) {
        this.courseService = courseService;
        this.courseVersionService = courseVersionService;
        this.responses = responses;
    }

    @GetMapping("/teacher/courses")
    @PreAuthorize("hasAuthority('" + CoursePermissions.UPDATE + "')")
    public ApiResponse<PageResponse<TeacherCourseResponse>> listCourses(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(courseService.listTeacherCourses(
                JwtSecurityUtils.userId(jwt), page, size));
    }

    @PostMapping("/courses")
    @PreAuthorize("hasAuthority('" + CoursePermissions.CREATE + "')")
    public ApiResponse<CourseDraftResponse> createCourse(
            @Valid @RequestBody CourseCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(courseService.createCourse(JwtSecurityUtils.userId(jwt), request,
                JwtSecurityUtils.roles(jwt)));
    }

    @GetMapping("/teacher/courses/{courseId}/draft")
    @PreAuthorize("hasAuthority('" + CoursePermissions.UPDATE + "')")
    public ApiResponse<CourseDraftResponse> currentDraft(
            @PathVariable Long courseId,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(courseVersionService.getCurrentDraft(courseId, JwtSecurityUtils.userId(jwt)));
    }

    @PostMapping("/courses/{courseId}/drafts")
    @PreAuthorize("hasAuthority('" + CoursePermissions.UPDATE + "')")
    public ApiResponse<CourseDraftResponse> createDraft(
            @PathVariable Long courseId,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(
                courseVersionService.createDraftFromPublishedOrRejected(courseId, JwtSecurityUtils.userId(jwt)));
    }

    @PutMapping("/course-drafts/{versionId}")
    @PreAuthorize("hasAuthority('" + CoursePermissions.UPDATE + "')")
    public ApiResponse<CourseDraftResponse> updateDraft(
            @PathVariable Long versionId,
            @Valid @RequestBody CourseDraftUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(
                courseVersionService.updateDraft(versionId, JwtSecurityUtils.userId(jwt), request));
    }
}
