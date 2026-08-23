package com.educloud.course.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.security.AuthenticatedUser;
import com.educloud.course.dto.request.ReviewUpsertRequest;
import com.educloud.course.dto.response.CourseReviewResponse;
import com.educloud.course.security.JwtSecurityUtils;
import com.educloud.course.service.CourseReviewService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课程评价控制器（M05 任务 14）：POST /courses/{id}/reviews（已选课学生 upsert）、
 * DELETE /course-reviews/{id}（管理角色隐藏）。
 *
 * <p>依据：规格 §6 —— POST 登录即可（服务层校验 ACTIVE 选课，未选课 → 403
 * NOT_ENROLLED），rating 1-5 由 @Valid 校验（越界/缺失 → 400 VALIDATION_FAILED）；
 * DELETE 为管理端动作：course:* 权限码无 review 专用码且安全链只映射 permissions
 * claim（hasRole 不可达），故不配 @PreAuthorize，管理角色判定在服务层按 JWT roles
 * claim 完成（SYSTEM_ADMIN/SUPER_ADMIN，见 CourseReviewService 注释）。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class CourseReviewController {

    private final CourseReviewService reviewService;
    private final ApiResponseFactory responses;

    public CourseReviewController(CourseReviewService reviewService, ApiResponseFactory responses) {
        this.reviewService = reviewService;
        this.responses = responses;
    }

    @PostMapping("/courses/{courseId}/reviews")
    public ApiResponse<CourseReviewResponse> upsert(
            @PathVariable Long courseId,
            @Valid @RequestBody ReviewUpsertRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(
                reviewService.upsert(courseId, JwtSecurityUtils.userId(jwt), request));
    }

    @DeleteMapping("/course-reviews/{reviewId}")
    public ApiResponse<CourseReviewResponse> hide(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = JwtSecurityUtils.authenticatedUser(jwt);
        return responses.success(
                reviewService.hide(reviewId, JwtSecurityUtils.userId(jwt), user.roles()));
    }
}
