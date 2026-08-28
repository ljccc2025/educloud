package com.educloud.content.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ExamAttemptResponse {
    private Long id;
    private Long examId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private Integer score;
    private Boolean passed;
    private Boolean timeout;
    private Integer tabSwitchCount;
    private Map<Long, List<Integer>> answers;
    private List<ExamQuestionResult> results;

    @Data
    @Builder
    public static class ExamQuestionResult {
        private Long questionId;
        private String questionType;
        private String stem;
        private List<String> options;
        private List<Integer> answer;
        private Integer score;
        private Boolean correct;
    }
}
