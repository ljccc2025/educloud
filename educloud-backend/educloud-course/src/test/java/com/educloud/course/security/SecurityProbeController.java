package com.educloud.course.security;

import com.educloud.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** 仅测试用受保护端点：验证 Resource Server 权限码映射与方法安全。 */
@RestController
public class SecurityProbeController {

    @GetMapping("/api/v1/courses/security-probe")
    @PreAuthorize("hasAuthority('course:audit')")
    public ApiResponse<String> probe() {
        return new ApiResponse<>("SUCCESS", "OK", "probe-ok", "security-test", Instant.now());
    }
}