package com.educloud.order.feign;

import com.educloud.common.api.ApiResponse;
import com.educloud.order.feign.dto.CourseSalesSnapshotDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "educloud-course", url = "${educloud.course-service-url:}")
public interface CourseClient {

    @GetMapping("/api/v1/courses/{courseId}")
    ApiResponse<CourseSalesSnapshotDto> getCourseDetail(@PathVariable("courseId") Long courseId);
}
