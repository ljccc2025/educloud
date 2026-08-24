package com.educloud.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CoursewareUpdateRequest {
    @NotBlank(message = "Courseware title must not be blank")
    @Size(max = 128, message = "Courseware title must not exceed 128 characters")
    private String title;

    @NotBlank(message = "Courseware type must not be blank")
    private String coursewareType;

    private Long fileId;

    @Size(max = 1024, message = "External URL must not exceed 1024 characters")
    private String externalUrl;

    private Integer durationSeconds;

    private Long sizeBytes;

    private Boolean freePreview;

    @NotNull(message = "Sort order must not be null")
    private Integer sortOrder;
}
