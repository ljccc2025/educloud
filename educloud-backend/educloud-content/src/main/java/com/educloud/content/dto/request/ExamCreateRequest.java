package com.educloud.content.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExamCreateRequest {
    @NotNull
    private Long courseId;
    @NotBlank
    private String title;
    private String description;
    @Positive
    private Integer durationMinutes;
    @Positive
    private Integer passScore;
    @NotNull
    private LocalDateTime startTime;
    @NotNull
    private LocalDateTime endTime;
    @NotEmpty
    private List<@Valid PaperItem> paper;

    @Data
    public static class PaperItem {
        @NotNull
        private Long questionId;
        @Positive
        private Integer score;
    }
}
