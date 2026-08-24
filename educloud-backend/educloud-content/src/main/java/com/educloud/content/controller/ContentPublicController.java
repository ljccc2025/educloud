package com.educloud.content.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.content.dto.response.ChapterResponse;
import com.educloud.content.security.JwtSecurityUtils;
import com.educloud.content.service.CourseContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class ContentPublicController {

    private final CourseContentService courseContentService;
    private final ApiResponseFactory responses;

    @GetMapping("/{courseId}/chapters")
    public ApiResponse<List<ChapterResponse>> getCourseChapters(
            @PathVariable Long courseId,
            @AuthenticationPrincipal Jwt jwt) {
        Long studentId = (jwt != null) ? JwtSecurityUtils.userId(jwt) : null;
        List<ChapterResponse> chapters = courseContentService.getPublishedChapters(courseId, studentId);
        return responses.success(chapters);
    }
}
