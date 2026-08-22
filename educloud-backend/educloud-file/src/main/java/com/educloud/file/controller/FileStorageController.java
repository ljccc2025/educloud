package com.educloud.file.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.file.dto.response.StorageStatusResponse;
import com.educloud.file.dto.response.StorageTestResponse;
import com.educloud.file.support.StorageStatusService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * 对外存储状态与探测控制器。
 *
 * <p>依据：M04 设计规格 6.1/9 节 —— storage-status 需 file:storage:status:read、
 * storage-tests 需 file:storage:test（另有 Redis 限频过滤器）；两者响应均不携带
 * accessKey/secretKey。探测与审计逻辑收敛在 {@link StorageStatusService}。</p>
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileStorageController {

    private final StorageStatusService storageStatusService;
    private final ApiResponseFactory responses;

    public FileStorageController(StorageStatusService storageStatusService, ApiResponseFactory responses) {
        this.storageStatusService = Objects.requireNonNull(storageStatusService, "storageStatusService");
        this.responses = Objects.requireNonNull(responses, "responses");
    }

    @GetMapping("/storage-status")
    @PreAuthorize("hasAuthority('file:storage:status:read')")
    public ApiResponse<StorageStatusResponse> status() {
        return responses.success(storageStatusService.status());
    }

    @PostMapping("/storage-tests")
    @PreAuthorize("hasAuthority('file:storage:test')")
    public ApiResponse<StorageTestResponse> runTest(@AuthenticationPrincipal Jwt jwt) {
        return responses.success(storageStatusService.runTest(Long.valueOf(jwt.getSubject())));
    }
}
