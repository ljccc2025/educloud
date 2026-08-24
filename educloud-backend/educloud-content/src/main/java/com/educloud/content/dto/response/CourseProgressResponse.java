package com.educloud.content.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CourseProgressResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long courseId;

    private Integer completedCoursewareCount;

    private Integer totalCoursewareCount;

    private Integer progressPercent;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long lastLearnedCoursewareId;

    private LocalDateTime updatedAt;
}
