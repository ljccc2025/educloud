package com.educloud.content.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class CourseContentReadinessResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contentRootId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long courseId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long publishedRevisionId;

    private Boolean ready;

    private Long aggregateVersion;
}
