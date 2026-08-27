package com.educloud.content.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResponse {
    private String id;
    private String courseId;
    private String courseTitle;
    private String courseName;
    private String title;
    private String description;
    private String dueDate;
    private Integer totalScore;
    private String status; // DRAFT, PUBLISHED, SUBMITTED, GRADED, OVERDUE
    private Boolean allowLateSubmission;
    private Integer maxAttempts;
    private Integer submissionCount;
    private Integer gradedCount;
    private String publishedAt;
    private Integer score;
    private String submitDate;
    private String feedback;
    private Map<String, Object> submission;
    private List<Map<String, Object>> submissions;
}
