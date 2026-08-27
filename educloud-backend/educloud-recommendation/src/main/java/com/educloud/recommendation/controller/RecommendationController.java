package com.educloud.recommendation.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.recommendation.dto.request.FeedbackRequest;
import com.educloud.recommendation.dto.response.RecommendationResponse;
import com.educloud.recommendation.exception.RecommendationErrorCode;
import com.educloud.recommendation.service.FeedbackService;
import com.educloud.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 推荐中心接口（M13 任务 7）。
 *
 * <p>GET /api/v1/recommendations 匿名可达（Gateway PUBLIC_READ 匿名放行后转发不带
 * Authorization 头，服务内同步放行，见 SecurityConfig）；已登录用户自动过滤已学课程与
 * DISLIKE 课程。POST /api/v1/recommendations/feedback 需要登录（写入「不感兴趣」）。
 * 用户身份经 {@link Jwt} 取自已验证 JWT 的 sub claim，userId 为雪花数字字符串
 * （与 course JwtSecurityUtils 的 Long.valueOf(sub) 同一转换约定；非数字 sub 视为
 * 无效令牌抛 UNAUTHENTICATED）。</p>
 */
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private static final Set<String> SUPPORTED_CONTEXTS = Set.of("home", "course");
    private static final String DISLIKE_ACTION = "DISLIKE";

    private final RecommendationService recommendationService;
    private final FeedbackService feedbackService;
    private final ApiResponseFactory responses;

    @GetMapping
    public ApiResponse<RecommendationResponse> recommend(
            @RequestParam String context,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "10") Integer limit,
            @AuthenticationPrincipal Jwt jwt) {
        if (!SUPPORTED_CONTEXTS.contains(context)) {
            throw new BusinessException(
                    RecommendationErrorCode.RECOMMENDATION_CONTEXT_INVALID,
                    RecommendationErrorCode.RECOMMENDATION_CONTEXT_INVALID.defaultMessage());
        }
        if ("course".equals(context) && courseId == null) {
            throw new BusinessException(
                    RecommendationErrorCode.RECOMMENDATION_COURSE_ID_REQUIRED,
                    RecommendationErrorCode.RECOMMENDATION_COURSE_ID_REQUIRED.defaultMessage());
        }
        Long userId = userId(jwt);
        Set<Long> disliked = feedbackService.dislikedCourseIds(userId);
        return responses.success(recommendationService.recommend(userId, courseId, limit, disliked));
    }

    @PostMapping("/feedback")
    public ApiResponse<Void> feedback(@Valid @RequestBody FeedbackRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {
        if (!DISLIKE_ACTION.equals(request.getAction())) {
            throw new BusinessException(
                    RecommendationErrorCode.RECOMMENDATION_ACTION_UNSUPPORTED,
                    "当前仅支持 DISLIKE");
        }
        // 安全链已保证 authenticated，jwt 非空；非数字 sub 视为无效令牌（与 course
        // JwtSecurityUtils.userId 的 401 语义一致，统一走 userId(Jwt)）。
        Long userId = userId(jwt);
        feedbackService.dislike(userId, request.getCourseId(), request.getReason());
        return responses.success(null);
    }

    /**
     * 解析 JWT sub（userId，数字字符串）为 Long；匿名请求（jwt 为 null）返回 null；
     * 非数字 sub 视为无效令牌抛 UNAUTHENTICATED（与 course JwtSecurityUtils.userId 的
     * 401 语义一致）。
     */
    private Long userId(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED, "无效令牌");
        }
    }
}
