package com.educloud.content.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ExamBankQuestionResponse {
    private Long id;
    private Long courseId;
    private String questionType;
    private String stem;
    private List<String> options;
    private Integer defaultScore;
    private LocalDateTime createdAt;
}
