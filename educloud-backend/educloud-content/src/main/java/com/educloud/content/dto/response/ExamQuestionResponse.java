package com.educloud.content.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ExamQuestionResponse {
    private Long id;
    private String questionType;
    private String stem;
    private List<String> options;
    private Integer score;
}
