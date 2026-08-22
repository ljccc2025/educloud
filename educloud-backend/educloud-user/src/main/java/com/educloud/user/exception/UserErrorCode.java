package com.educloud.user.exception;

import com.educloud.common.error.ErrorCode;

/**
 * User 服务域错误码。依据：M03 设计规格第 10.3 节（错误码示例）与 API 规范第 4 节。
 * 通用错误（校验/版本冲突/限流/依赖不可用）复用 CommonErrorCode。
 */
public enum UserErrorCode implements ErrorCode {

    REGISTRATION_DISABLED(403, "Student registration is disabled"),
    USERNAME_TAKEN(409, "Username is already taken"),
    EMAIL_TAKEN(409, "Email is already registered"),
    PHONE_TAKEN(409, "Phone is already registered"),
    PASSWORD_WEAK(422, "Password does not meet the policy"),
    INVALID_CREDENTIALS(401, "Invalid credentials"),
    ACCOUNT_LOCKED(423, "Account is temporarily locked"),
    ACCOUNT_DISABLED(403, "Account is disabled"),
    REFRESH_ALREADY_ROTATED(409, "Refresh token was already rotated"),
    SESSION_REVOKED(401, "Session has been revoked"),
    TOKEN_EXPIRED(401, "Token has expired"),
    SESSION_REUSE_DETECTED(401, "Session reuse detected"),
    USER_NOT_FOUND(404, "User not found"),
    ROLE_NOT_FOUND(404, "Role not found"),
    ROLE_CODE_TAKEN(409, "Role code is already taken"),
    BUILT_IN_ROLE_PROTECTED(403, "Built-in roles cannot be modified"),
    PLATFORM_CONFIG_NOT_FOUND(404, "Platform config not found"),
    IDEMPOTENCY_CONFLICT(409, "Idempotency key was used with a different request"),
    SERVICE_CLIENT_NOT_FOUND(401, "Unknown service client"),
    SERVICE_CLIENT_DISABLED(403, "Service client is disabled"),
    SERVICE_CREDENTIAL_INVALID(401, "Service client credential is invalid"),
    SERVICE_TOKEN_SCOPE_DENIED(403, "Requested scope is not allowed for this client");

    private final int httpStatus;
    private final String defaultMessage;

    UserErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
