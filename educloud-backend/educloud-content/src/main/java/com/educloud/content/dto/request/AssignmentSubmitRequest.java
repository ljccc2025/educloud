package com.educloud.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AssignmentSubmitRequest {
    @NotBlank(message = "作答内容不能为空")
    private String content;
    private List<Map<String, Object>> files;
    private String note;
    private String studentName;
    private String studentAvatar;
}
