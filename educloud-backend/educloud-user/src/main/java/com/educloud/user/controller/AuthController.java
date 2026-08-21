package com.educloud.user.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.user.config.SessionProperties;
import com.educloud.user.dto.request.LoginRequest;
import com.educloud.user.dto.request.PasswordChangeRequest;
import com.educloud.user.dto.request.RegisterStudentRequest;
import com.educloud.user.dto.response.LoginResponse;
import com.educloud.user.dto.response.RegisteredUserResponse;
import com.educloud.user.entity.RefreshSessionEntity;
import com.educloud.user.mapper.RefreshSessionMapper;
import com.educloud.user.service.AuthenticationService;
import com.educloud.user.service.IdempotencyService;
import com.educloud.user.service.PasswordChangeService;
import com.educloud.user.service.RefreshSessionService;
import com.educloud.user.service.RegistrationService;
import com.educloud.user.service.SessionRevocationService;
import com.educloud.user.session.SessionFactory;
import com.educloud.user.support.ClientFingerprint;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;

/**
 * 认证控制器（注册/登录/刷新/注销/改密）。
 * 依据：API 规范第 7 节（外部经 Gateway 访问 /api/v1/auth/**）；Refresh Token 只写 HttpOnly Cookie。
 */
@RestController
@RequestMapping("/api/v1/auth")
public final class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String REGISTER_OPERATION = "student-register";

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final RefreshSessionService refreshSessionService;
    private final PasswordChangeService passwordChangeService;
    private final RefreshSessionMapper refreshSessionMapper;
    private final SessionRevocationService revocationService;
    private final IdempotencyService idempotencyService;
    private final SessionProperties sessionProperties;
    private final ApiResponseFactory responses;
    private final ObjectMapper objectMapper;

    public AuthController(
            RegistrationService registrationService,
            AuthenticationService authenticationService,
            RefreshSessionService refreshSessionService,
            PasswordChangeService passwordChangeService,
            RefreshSessionMapper refreshSessionMapper,
            SessionRevocationService revocationService,
            IdempotencyService idempotencyService,
            SessionProperties sessionProperties,
            ApiResponseFactory responses,
            ObjectMapper objectMapper) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
        this.refreshSessionService = refreshSessionService;
        this.passwordChangeService = passwordChangeService;
        this.refreshSessionMapper = refreshSessionMapper;
        this.revocationService = revocationService;
        this.idempotencyService = idempotencyService;
        this.sessionProperties = sessionProperties;
        this.responses = responses;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisteredUserResponse>> register(
            @Valid @RequestBody RegisterStudentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest servletRequest) throws JsonProcessingException {
        String requestId = servletRequest.getHeader("X-Request-Id");
        String ip = servletRequest.getRemoteAddr();
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);

        // 幂等重放（匿名注册 user_id 用 0 约定，设计规格第 7 节）。
        Optional<IdempotencyService.StoredResponse> replay = idempotencyService.findReplay(
                REGISTER_OPERATION, idempotencyKey, 0L, requestHash(request));
        if (replay.isPresent()) {
            return replayResponse(replay.get());
        }

        Long userId = registrationService.register(request, ip, userAgent, requestId);
        ApiResponse<RegisteredUserResponse> body = responses.success(
                new RegisteredUserResponse(String.valueOf(userId)));
        idempotencyService.record(
                REGISTER_OPERATION, idempotencyKey, 0L, requestHash(request),
                HttpStatus.CREATED.value(), objectMapper.writeValueAsString(body));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        String requestId = servletRequest.getHeader("X-Request-Id");
        String ip = servletRequest.getRemoteAddr();
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);

        AuthenticationService.LoginResult result =
                authenticationService.login(request, ip, userAgent, requestId);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshTokenRaw()).toString())
                .body(responses.success(result.response()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @CookieValue(value = "refresh_token", required = false) String refreshToken,
            HttpServletRequest servletRequest) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String requestId = servletRequest.getHeader("X-Request-Id");
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);
        RefreshSessionService.RefreshResult result = refreshSessionService.refresh(
                refreshToken, ClientFingerprint.of(userAgent), requestId);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshTokenRaw()).toString())
                .body(responses.success(result.response()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = "refresh_token", required = false) String refreshToken,
            HttpServletRequest servletRequest) {
        String requestId = servletRequest.getHeader("X-Request-Id");
        if (refreshToken != null && !refreshToken.isBlank()) {
            revocationService.revokeFamilyByToken(refreshToken, "LOGOUT", requestId);
        }
        ResponseCookie cleared = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(sessionProperties.cookieSecure())
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .build();
    }

    /**
     * 修改密码（Bearer 保护）。旧 Access 全部失效；当前会话族保留可刷新（设计规格第 4.4 节撤销矩阵）。
     * currentFamilyId 由 Refresh Token Cookie 反查；无 Cookie 时撤销该用户全部会话族。
     */
    @PostMapping("/password/change")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordChangeRequest request,
            @CookieValue(value = "refresh_token", required = false) String refreshToken,
            HttpServletRequest servletRequest) {
        String familyId = null;
        if (refreshToken != null && !refreshToken.isBlank()) {
            RefreshSessionEntity session = refreshSessionMapper.selectByTokenHashForUpdate(
                    SessionFactory.sha256Hex(refreshToken));
            if (session != null) {
                familyId = session.getFamilyId();
            }
        }
        passwordChangeService.changePassword(
                Long.valueOf(jwt.getSubject()),
                request.oldPassword(),
                request.newPassword(),
                familyId,
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader(HttpHeaders.USER_AGENT),
                servletRequest.getHeader("X-Request-Id"));
        return responses.success(null);
    }

    private ResponseCookie refreshCookie(String rawToken) {
        return ResponseCookie.from(REFRESH_COOKIE, rawToken)
                .httpOnly(true)
                .secure(sessionProperties.cookieSecure())
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(Duration.ofSeconds(sessionProperties.refreshTokenTtl().getSeconds()))
                .build();
    }

    private String requestHash(RegisterStudentRequest request) {
        return com.educloud.user.session.SessionFactory.sha256Hex(
                request.username() + "|" + request.email() + "|" + request.phone() + "|" + request.displayName());
    }

    private ResponseEntity<ApiResponse<RegisteredUserResponse>> replayResponse(
            IdempotencyService.StoredResponse stored) {
        try {
            @SuppressWarnings("unchecked")
            ApiResponse<RegisteredUserResponse> body = objectMapper.readValue(
                    stored.bodyJson(),
                    objectMapper.getTypeFactory().constructParametricType(
                            ApiResponse.class, RegisteredUserResponse.class));
            return ResponseEntity.status(stored.status()).body(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored idempotency response cannot be parsed", exception);
        }
    }
}