package com.educloud.file.exception;

/**
 * 会话归属校验失败：调用方 uploaderId 与会话 uploader_id 不一致。
 *
 * <p>任务 4 内部异常；任务 7 统一映射为 {@code FILE_ACCESS_DENIED(403)}。</p>
 */
public class UploadSessionAccessDeniedException extends RuntimeException {

    public UploadSessionAccessDeniedException(String message) {
        super(message);
    }

    public UploadSessionAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}

