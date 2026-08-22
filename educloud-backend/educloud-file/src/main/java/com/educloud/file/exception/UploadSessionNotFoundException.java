package com.educloud.file.exception;

/**
 * 上传会话不存在（complete 时 selectByIdForUpdate 无结果）。
 *
 * <p>任务 4 内部异常；任务 7 统一映射为 {@code UPLOAD_SESSION_NOT_FOUND(404)}。</p>
 */
public class UploadSessionNotFoundException extends RuntimeException {

    public UploadSessionNotFoundException(String message) {
        super(message);
    }

    public UploadSessionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

