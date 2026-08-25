package com.educloud.content.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * course 服务内部调用配置（educloud.content.course）：CourseClient 调用
 * GET /internal/v1/courses/{courseId}/enrollments/{studentId} 校验学员报名状态
 * （BUG-002 修复）。enabled=false 仅限本地未起 course 服务时跳过报名校验
 * （fail-open + WARN 日志），生产环境必须保持 true（fail-closed）。
 */
@ConfigurationProperties(prefix = "educloud.content.course")
public record ContentCourseProperties(
        String endpoint,
        String clientId,
        String clientSecret,
        boolean enabled,
        Duration timeout,
        String tokenEndpoint) {
}
