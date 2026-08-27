package com.educloud.analytics.controller;

import com.educloud.analytics.dto.response.ActivityItem;
import com.educloud.analytics.entity.ActivityFeedEntity;
import com.educloud.analytics.security.JwtSecurityUtils;
import com.educloud.analytics.service.ActivityFeedService;
import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 角色化动态流查询接口（规格 2026-08-27-activity-feed-certificate-design.md §7）。
 *
 * <p>按当前登录用户 + 角色过滤 {@code activity_feed}：limit 默认 10、上限 50；
 * 时间返回 ISO {@code timestamp}（避免前端 Invalid Date）；无动态返回空数组。</p>
 *
 * <p>注意：本控制器接管 {@code GET /api/v1/analytics/teacher/activities}（原
 * {@code TeacherAnalyticsController} 中基于审计日志的同路径端点已移除，避免映射冲突）。</p>
 */
@Slf4j
@Tag(name = "角色化动态流接口")
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class ActivityFeedController {

    private static final String ROLE_STUDENT = "STUDENT";
    private static final String ROLE_TEACHER = "TEACHER";
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final ActivityFeedService activityFeedService;
    private final ApiResponseFactory responses;
    private final ObjectMapper objectMapper;

    @Operation(summary = "获取当前登录学员的学习动态")
    @GetMapping("/student/activities")
    public ApiResponse<List<ActivityItem>> studentActivities(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request,
            @RequestParam(name = "limit", required = false, defaultValue = "10") Integer limit) {
        String studentId = JwtSecurityUtils.extractStudentId(jwt, request);
        return responses.success(toItems(activityFeedService.listActivities(studentId, ROLE_STUDENT, clampLimit(limit))));
    }

    @Operation(summary = "获取当前登录教师的教学动态")
    @GetMapping("/teacher/activities")
    public ApiResponse<List<ActivityItem>> teacherActivities(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request,
            @RequestParam(name = "limit", required = false, defaultValue = "10") Integer limit) {
        String teacherId = JwtSecurityUtils.extractTeacherId(jwt, request);
        return responses.success(toItems(activityFeedService.listActivities(teacherId, ROLE_TEACHER, clampLimit(limit))));
    }

    /** limit 钳制：默认 10，范围 [1, 50]（规格 §7）。 */
    private int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private List<ActivityItem> toItems(List<ActivityFeedEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream().map(this::toItem).toList();
    }

    private ActivityItem toItem(ActivityFeedEntity entity) {
        Map<String, Object> extra = parseExtra(entity.getExtraJson());
        return ActivityItem.builder()
                .id(entity.getId() != null ? String.valueOf(entity.getId()) : null)
                .actionType(entity.getActionType())
                .action(buildActionText(entity.getActionType(), entity.getTargetTitle(), extra))
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .targetTitle(entity.getTargetTitle())
                .extra(extra)
                .timestamp(entity.getOccurredAt() != null ? entity.getOccurredAt().toString() : null)
                .build();
    }

    /** 动作文案模板（规格 §4.1）；未定义类型兜底返回 actionType 本身。 */
    private String buildActionText(String actionType, String title, Map<String, Object> extra) {
        if (actionType == null) {
            return "";
        }
        String t = title != null ? title : "";
        return switch (actionType) {
            case "ENROLLED" -> "你报名了《" + t + "》";
            case "ASSIGNMENT_SUBMITTED" -> "你提交了《" + t + "》的作业";
            case "ASSIGNMENT_GRADED" -> "你的《" + t + "》作业已批改：" + extraValue(extra, "score", "") + " 分";
            case "COURSE_COMPLETED" -> "你完成了《" + t + "》";
            case "COURSE_REVIEWED" -> "你评价了《" + t + "》：" + extraValue(extra, "rating", "") + " 星";
            case "CERTIFICATE_ISSUED" -> "你获得了《" + t + "》完课证书";
            case "PROGRESS_MILESTONE" -> "你的《" + t + "》进度达到 " + extraValue(extra, "progress", "") + "%";
            case "COURSE_CREATED" -> "你创建了《" + t + "》";
            case "COURSE_UPDATED" -> "你更新了《" + t + "》";
            case "COURSE_PUBLISHED" -> "你发布了《" + t + "》";
            case "STUDENT_ENROLLED" -> "有学员报名了《" + t + "》";
            case "STUDENT_SUBMITTED" -> "有学员提交了《" + t + "》作业";
            case "STUDENT_REVIEWED" -> "有学员评价了《" + t + "》：" + extraValue(extra, "rating", "") + " 星";
            default -> actionType;
        };
    }

    private String extraValue(Map<String, Object> extra, String key, String fallback) {
        if (extra == null) {
            return fallback;
        }
        Object value = extra.get(key);
        return value != null ? String.valueOf(value) : fallback;
    }

    private Map<String, Object> parseExtra(String extraJson) {
        if (extraJson == null || extraJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(extraJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse activity extra_json, extra dropped: {}", e.getMessage());
            return null;
        }
    }
}
