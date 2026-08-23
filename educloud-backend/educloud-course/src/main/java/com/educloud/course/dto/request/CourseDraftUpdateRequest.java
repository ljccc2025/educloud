package com.educloud.course.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 草稿全量更新请求体（PUT /api/v1/course-drafts/{versionId}，M05 任务 8）。
 *
 * <p>全量字段（title/subtitle/description/coverFileId/level/price/currency/categoryId）：
 * PUT 语义为整体替换，可空字段传 null 表示清空。coverFileId/categoryId 为 Snowflake ID：
 * DTO 一律 String（规格 §6，前端禁止 Number()），@Pattern 限定 1-19 位数字，service 层用
 * Long.parseLong 解析（格式错误 → 400）。校验规则与 {@link CourseCreateRequest} 一致
 * （categoryId 必填，对齐 V001 course_version.category_id NOT NULL）。</p>
 */
public record CourseDraftUpdateRequest(
        @NotBlank
        @Size(max = 255)
        String title,

        @Size(max = 255)
        String subtitle,

        @Size(max = 10000)
        String description,

        @Pattern(regexp = "\\d{1,19}", message = "coverFileId is invalid")
        String coverFileId,

        @NotBlank
        @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED", message = "level is invalid")
        String level,

        @NotNull
        @DecimalMin(value = "0.00", message = "price must be >= 0")
        @Digits(integer = 8, fraction = 2, message = "price must have at most 8 integer and 2 fraction digits")
        BigDecimal price,

        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "currency is invalid")
        String currency,

        @NotNull
        @Pattern(regexp = "\\d{1,19}", message = "categoryId is invalid")
        String categoryId) {
}
