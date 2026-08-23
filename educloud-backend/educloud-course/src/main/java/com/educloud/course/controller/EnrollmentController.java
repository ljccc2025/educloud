package com.educloud.course.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.course.dto.response.CourseStudentResponse;
import com.educloud.course.dto.response.EnrollmentResponse;
import com.educloud.course.dto.response.MyCourseResponse;
import com.educloud.course.security.CoursePermissions;
import com.educloud.course.security.JwtSecurityUtils;
import com.educloud.course.service.EnrollmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 选课控制器（M05 任务 13）：POST /courses/{id}/enrollments（course:enroll，幂等）、
 * GET /me/enrollments（学生我的课程）、GET /courses/{id}/students（course:student:read，
 * 服务层归属校验）。
 *
 * <p>依据：规格 §6 —— 免费选课幂等返回现状 200；我的课程登录即可；学生列表需
 * course:student:read + course_teacher 归属（TeacherAccessGuard 服务内硬规则）。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final ApiResponseFactory responses;

    public EnrollmentController(EnrollmentService enrollmentService, ApiResponseFactory responses) {
        this.enrollmentService = enrollmentService;
        this.responses = responses;
    }

    @PostMapping("/courses/{courseId}/enrollments")
    @PreAuthorize("hasAuthority('" + CoursePermissions.ENROLL + "')")
    public ApiResponse<EnrollmentResponse> enroll(
            @PathVariable Long courseId,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(enrollmentService.enroll(courseId, JwtSecurityUtils.userId(jwt),
                JwtSecurityUtils.roles(jwt)));
    }

    @GetMapping("/me/enrollments")
    public ApiResponse<PageResponse<MyCourseResponse>> myCourses(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(
                enrollmentService.myCourses(JwtSecurityUtils.userId(jwt), page, size));
    }

    @GetMapping("/courses/{courseId}/students")
    @PreAuthorize("hasAuthority('" + CoursePermissions.STUDENT_READ + "')")
    public ApiResponse<PageResponse<CourseStudentResponse>> students(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(enrollmentService.listStudents(
                courseId, JwtSecurityUtils.userId(jwt), page, size));
    }
}
