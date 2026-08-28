package com.educloud.content.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ExamResponse {
    private Long id;
    private Long courseId;
    private String courseTitle;
    private String title;
    private String description;
    private Integer durationMinutes;
    private Integer totalScore;
    private Integer passScore;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private List<ExamQuestionResponse> questions;
    /** 题目数量：已批改态不下发 questions，题数需单独提供。 */
    private Integer questionCount;
    private Integer score;
    private Boolean passed;
    private String attemptStatus;
}
