package com.educloud.course.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 评价 upsert 请求体（POST /api/v1/courses/{id}/reviews，M05 任务 14）。
 *
 * <p>规格 §6：rating 1-5 必填；content 可空。Bean Validation 负责 HTTP 入口 400
 * （MethodArgumentNotValidException → VALIDATION_FAILED），服务层对 rating 再做兜底
 * 校验（防御绕过入口的调用方，与 SnowflakeIds.parse 同一风格）。</p>
 */
public record ReviewUpsertRequest(
        @NotNull(message = "rating is required")
        @Min(value = 1, message = "rating must be between 1 and 5")
        @Max(value = 5, message = "rating must be between 1 and 5")
        Integer rating,
        @Size(max = 10000, message = "content must not exceed 10000 characters")
        String content) {
}
