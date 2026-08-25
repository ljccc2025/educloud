package com.educloud.live.feign;

import com.educloud.live.feign.dto.CourseEnrollmentStatusResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "educloud-course", url = "${educloud.course.endpoint:}")
public interface CourseClient {

    @GetMapping("/internal/v1/courses/{courseId}/enrollments/{studentId}")
    CourseEnrollmentStatusResponse getEnrollmentStatus(
            @PathVariable("courseId") Long courseId,
            @PathVariable("studentId") Long studentId,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader(value = "X-Client-Id", defaultValue = "educloud-live") String clientId);
}
