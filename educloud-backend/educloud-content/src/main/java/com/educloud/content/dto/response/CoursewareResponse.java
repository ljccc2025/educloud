package com.educloud.content.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class CoursewareResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long chapterId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long courseId;

    private String title;

    private String coursewareType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    private String externalUrl;

    private Integer durationSeconds;

    private Long sizeBytes;

    private Boolean freePreview;

    private Integer sortOrder;

    private Boolean completed;

    private Integer positionSeconds;
}
