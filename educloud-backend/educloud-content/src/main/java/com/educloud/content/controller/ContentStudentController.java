package com.educloud.content.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.content.dto.request.ProgressReportRequest;
import com.educloud.content.dto.response.CourseProgressResponse;
import com.educloud.content.dto.response.CoursewareDownloadUrlResponse;
import com.educloud.content.security.JwtSecurityUtils;
import com.educloud.content.service.CourseProgressService;
import com.educloud.content.service.CoursewareAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ContentStudentController {

    private final CoursewareAccessService accessService;
    private final CourseProgressService progressService;
    private final ApiResponseFactory responses;

    @GetMapping("/coursewares/{coursewareId}/download-url")
    public ApiResponse<CoursewareDownloadUrlResponse> getDownloadUrl(
            @PathVariable Long coursewareId,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt != null ? JwtSecurityUtils.userId(jwt) : null;
        Set<String> roles = jwt != null ? JwtSecurityUtils.roles(jwt) : Set.of();
        Set<String> permissions = jwt != null ? JwtSecurityUtils.permissions(jwt) : Set.of();

        CoursewareDownloadUrlResponse urlResponse = accessService.getDownloadUrl(
                coursewareId, userId, roles, permissions);
        return responses.success(urlResponse);
    }

    @PutMapping("/coursewares/{coursewareId}/progress")
    public ApiResponse<CourseProgressResponse> reportProgress(
            @PathVariable Long coursewareId,
            @Valid @RequestBody ProgressReportRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        CourseProgressResponse progress = progressService.reportProgress(coursewareId, request, studentId);
        return responses.success(progress);
    }

    @GetMapping("/student/courses/{courseId}/progress")
    public ApiResponse<CourseProgressResponse> getCourseProgress(
            @PathVariable Long courseId,
            @AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        CourseProgressResponse progress = progressService.getCourseProgress(courseId, studentId);
        return responses.success(progress);
    }

    @PostMapping("/student/courses/progress/batch")
    public ApiResponse<List<CourseProgressResponse>> batchGetProgress(
            @RequestBody List<Long> courseIds,
            @AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        List<CourseProgressResponse> list = progressService.batchGetCourseProgress(courseIds, studentId);
        return responses.success(list);
    }
}
