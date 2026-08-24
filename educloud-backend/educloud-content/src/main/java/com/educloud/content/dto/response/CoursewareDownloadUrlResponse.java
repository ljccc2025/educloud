package com.educloud.content.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CoursewareDownloadUrlResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long coursewareId;

    private String downloadUrl;

    private LocalDateTime expiresAt;
}
