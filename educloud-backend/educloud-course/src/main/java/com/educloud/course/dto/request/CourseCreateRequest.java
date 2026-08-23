package com.educloud.course.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 建课请求体（POST /api/v1/courses，M05 任务 8）。
 *
 * <p>title/level/price/currency/categoryId 必填；subtitle/description/coverFileId 可空。
 * coverFileId/categoryId 为 Snowflake ID：DTO 一律 String（规格 §6：所有 Snowflake ID
 * 在 DTO 为 String，前端禁止 Number()，雪花 63 位 &gt; 2^53），@Pattern 限定 1-19 位数字，
 * service 层用 Long.parseLong 解析（格式错误 → 400）。categoryId 必填：V001__course.sql
 * 中 course_version.category_id 为 NOT NULL（计划草稿曾考虑“未分类”可空，但已提交的
 * DDL 以 NOT NULL 为权威；若未来引入“未分类”种子分类可再放宽）。price 为十进制金额
 * （DECIMAL(10,2)），0 元合法（免费课程）；currency 为 ISO 4217 三位大写字母。</p>
 */
public record CourseCreateRequest(
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
