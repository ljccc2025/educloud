package com.educloud.content.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.content.dto.request.ChapterCreateRequest;
import com.educloud.content.dto.request.ChapterUpdateRequest;
import com.educloud.content.dto.request.CoursewareCreateRequest;
import com.educloud.content.dto.request.CoursewareUpdateRequest;
import com.educloud.content.dto.response.ChapterResponse;
import com.educloud.content.dto.response.ContentDraftResponse;
import com.educloud.content.dto.response.CoursewareResponse;
import com.educloud.content.security.TeacherAccessGuard;
import com.educloud.content.service.ChapterService;
import com.educloud.content.service.ContentRevisionService;
import com.educloud.content.service.CourseContentService;
import com.educloud.content.service.CoursewareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teacher")
@RequiredArgsConstructor
public class ContentTeacherController {

    private final CourseContentService courseContentService;
    private final ChapterService chapterService;
    private final CoursewareService coursewareService;
    private final ContentRevisionService revisionService;
    private final TeacherAccessGuard teacherAccessGuard;
    private final ApiResponseFactory responses;

    @GetMapping("/courses/{courseId}/content-draft")
    public ApiResponse<ContentDraftResponse> getContentDraft(
            @PathVariable Long courseId,
            @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = teacherAccessGuard.checkTeacherAccess(jwt);
        ContentDraftResponse draft = courseContentService.getOrCreateDraft(courseId, teacherId);
        return responses.success(draft);
    }

    @PostMapping("/courses/{courseId}/content-draft/new")
    public ApiResponse<ContentDraftResponse> createNewDraft(
            @PathVariable Long courseId,
            @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = teacherAccessGuard.checkTeacherAccess(jwt);
        ContentDraftResponse draft = courseContentService.cloneNewDraft(courseId, teacherId);
        return responses.success(draft);
    }

    @PostMapping("/courses/{courseId}/chapters")
    public ApiResponse<ChapterResponse> addChapter(
            @PathVariable Long courseId,
            @Valid @RequestBody ChapterCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = teacherAccessGuard.checkTeacherAccess(jwt);
        ChapterResponse chapter = chapterService.addChapter(courseId, request, teacherId);
        return responses.success(chapter);
    }

    @PutMapping("/chapters/{chapterId}")
    public ApiResponse<ChapterResponse> updateChapter(
            @PathVariable Long chapterId,
            @Valid @RequestBody ChapterUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = teacherAccessGuard.checkTeacherAccess(jwt);
        ChapterResponse chapter = chapterService.updateChapter(chapterId, request, teacherId);
        return responses.success(chapter);
    }

    @DeleteMapping("/chapters/{chapterId}")
    public ApiResponse<Void> deleteChapter(
            @PathVariable Long chapterId,
            @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = teacherAccessGuard.checkTeacherAccess(jwt);
        chapterService.deleteChapter(chapterId, teacherId);
        return responses.success(null);
    }

    @PostMapping("/chapters/{chapterId}/coursewares")
    public ApiResponse<CoursewareResponse> addCourseware(
            @PathVariable Long chapterId,
            @Valid @RequestBody CoursewareCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = teacherAccessGuard.checkTeacherAccess(jwt);
        CoursewareResponse courseware = coursewareService.addCourseware(chapterId, request, teacherId);
        return responses.success(courseware);
    }

    @PutMapping("/coursewares/{coursewareId}")
    public ApiResponse<CoursewareResponse> updateCourseware(
            @PathVariable Long coursewareId,
            @Valid @RequestBody CoursewareUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = teacherAccessGuard.checkTeacherAccess(jwt);
        CoursewareResponse courseware = coursewareService.updateCourseware(coursewareId, request, teacherId);
        return responses.success(courseware);
    }

    @DeleteMapping("/coursewares/{coursewareId}")
    public ApiResponse<Void> deleteCourseware(
            @PathVariable Long coursewareId,
            @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = teacherAccessGuard.checkTeacherAccess(jwt);
        coursewareService.deleteCourseware(coursewareId, teacherId);
        return responses.success(null);
    }

    @PostMapping("/content-revisions/{revisionId}/submit")
    public ApiResponse<Void> submitRevision(
            @PathVariable Long revisionId,
            @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = teacherAccessGuard.checkTeacherAccess(jwt);
        revisionService.submitReview(revisionId, teacherId);
        return responses.success(null);
    }

    @PostMapping("/content-revisions/{revisionId}/withdraw")
    public ApiResponse<Void> withdrawRevision(
            @PathVariable Long revisionId,
            @AuthenticationPrincipal Jwt jwt) {
        Long teacherId = teacherAccessGuard.checkTeacherAccess(jwt);
        revisionService.withdrawReview(revisionId, teacherId);
        return responses.success(null);
    }
}
