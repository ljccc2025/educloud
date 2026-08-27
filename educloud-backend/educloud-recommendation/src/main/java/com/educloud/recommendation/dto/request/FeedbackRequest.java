package com.educloud.recommendation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 推荐反馈请求体（M13 任务 7）。当前仅支持 DISLIKE（不感兴趣）。
 */
@Data
public class FeedbackRequest {

    @NotNull(message = "courseId 不能为空")
    private Long courseId;

    @NotBlank(message = "action 不能为空")
    private String action;

    private String reason;
}
