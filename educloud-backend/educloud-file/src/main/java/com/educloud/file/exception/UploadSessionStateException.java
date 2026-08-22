package com.educloud.file.exception;

/**
 * 上传会话状态不允许 complete（非 PENDING 的 COMPLETED/ABORTED 等）。
 *
 * <p>任务 4 内部异常；任务 7 统一错误码时由异常处理层选择映射（如 409 冲突）。</p>
 */
public class UploadSessionStateException extends RuntimeException {

    public UploadSessionStateException(String message) {
        super(message);
    }

    public UploadSessionStateException(String message, Throwable cause) {
        super(message, cause);
    }
}

