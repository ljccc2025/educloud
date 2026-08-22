package com.educloud.file.exception;

import com.educloud.common.error.ErrorCode;

/**
 * File 服务域错误码。依据：M04 设计规格错误码表与任务 7。
 *
 * <p>通用错误（校验/限流/依赖不可用）复用 CommonErrorCode；本枚举只承载 File
 * 域专属语义。code() 返回枚举名，与 API 规范第 4 节错误码命名一致。</p>
 */
public enum FileErrorCode implements ErrorCode {

    UPLOAD_SESSION_EXPIRED(410, "Upload session has expired"),
    UPLOAD_SESSION_NOT_FOUND(404, "Upload session not found"),
    UPLOAD_NOT_VERIFIED(409, "Upload could not be verified"),
    FILE_TYPE_NOT_ALLOWED(415, "File type is not allowed"),
    FILE_TOO_LARGE(413, "File is too large"),
    FILE_NOT_FOUND(404, "File not found"),
    FILE_BOUND(409, "File has active bindings"),
    FILE_ACCESS_DENIED(403, "File access denied"),
    GRANT_PURPOSE_NOT_ALLOWED(403, "Grant purpose is not allowed"),
    STORAGE_TEST_RATE_LIMITED(429, "Storage test rate limit exceeded"),
    VERSION_CONFLICT(409, "Resource version conflict"),
    FILE_STORAGE_UNAVAILABLE(503, "File storage unavailable");

    private final int httpStatus;
    private final String defaultMessage;

    FileErrorCode(int httpStatus, String defaultMessage) {
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
