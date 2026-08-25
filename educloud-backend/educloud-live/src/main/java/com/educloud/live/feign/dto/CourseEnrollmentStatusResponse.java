package com.educloud.live.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseEnrollmentStatusResponse {
    private String courseId;
    private String studentId;
    private String status;
    private boolean enrolled;
}
