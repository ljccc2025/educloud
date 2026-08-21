package com.educloud.user.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.user.dto.response.SigningKeyStatusResponse;
import com.educloud.user.service.SigningKeyStatusService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 签名公钥状态接口（security:key-status:read；API 规范第 7 节）。 */
@RestController
@RequestMapping("/api/v1/security")
public class SigningKeyStatusController {

    private final SigningKeyStatusService signingKeyStatusService;
    private final ApiResponseFactory responses;

    public SigningKeyStatusController(
            SigningKeyStatusService signingKeyStatusService, ApiResponseFactory responses) {
        this.signingKeyStatusService = signingKeyStatusService;
        this.responses = responses;
    }

    @GetMapping("/signing-key-status")
    @PreAuthorize("hasAuthority('security:key-status:read')")
    public ApiResponse<SigningKeyStatusResponse> status() {
        return responses.success(signingKeyStatusService.status());
    }
}
