package com.educloud.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChapterCreateRequest {
    @NotBlank(message = "Chapter title must not be blank")
    @Size(max = 128, message = "Chapter title must not exceed 128 characters")
    private String title;

    @Size(max = 512, message = "Chapter description must not exceed 512 characters")
    private String description;

    @NotNull(message = "Sort order must not be null")
    private Integer sortOrder;
}
