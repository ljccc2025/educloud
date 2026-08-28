package com.educloud.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class ExamQuestionRequest {
    @NotNull
    private Long courseId;
    @NotBlank
    private String questionType;
    @NotBlank
    private String stem;
    @NotNull
    private List<String> options;
    @NotNull
    private List<Integer> answer;
    private String analysis;
    @Positive
    private Integer defaultScore;
}
