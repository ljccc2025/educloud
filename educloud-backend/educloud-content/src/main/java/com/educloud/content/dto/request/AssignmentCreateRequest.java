package com.educloud.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignmentCreateRequest {
    @NotBlank(message = "courseId 不能为空")
    private String courseId;
    private String courseName;
    private String courseTitle;
    @NotBlank(message = "作业标题不能为空")
    private String title;
    private String description;
    private String dueDate;
    @NotNull(message = "满分不能为空")
    private Integer totalScore;
    private Boolean allowLateSubmission;
    private Integer maxAttempts;
}
