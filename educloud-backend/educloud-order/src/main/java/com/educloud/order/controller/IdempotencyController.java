package com.educloud.order.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.order.security.JwtSecurityUtils;
import com.educloud.order.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders/idempotency-token")
@RequiredArgsConstructor
public class IdempotencyController {

    private final IdempotencyService idempotencyService;
    private final ApiResponseFactory responses;

    @GetMapping
    public ApiResponse<String> getIdempotencyToken(@AuthenticationPrincipal Jwt jwt) {
        Long userId = JwtSecurityUtils.userId(jwt);
        return responses.success(idempotencyService.generateToken(userId));
    }
}
