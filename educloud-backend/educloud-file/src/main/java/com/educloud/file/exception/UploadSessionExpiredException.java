package com.educloud.file.exception;

/**
 * 上传会话已过期（显式 EXPIRED 状态或 PENDING 但 expiresAt 已到）。
 *
 * <p>任务 4 内部异常；任务 7 统一映射为 {@code UPLOAD_SESSION_EXPIRED(410)}。</p>
 */
public class UploadSessionExpiredException extends RuntimeException {

    public UploadSessionExpiredException(String message) {
        super(message);
    }

    public UploadSessionExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}

