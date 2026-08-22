package com.educloud.file.exception;

/**
 * complete 时对象存储中不存在目标对象（stat.exists=false）。
 *
 * <p>任务 4 内部异常；任务 7 统一映射为 {@code UPLOAD_NOT_VERIFIED(409)}。</p>
 */
public class UploadNotVerifiedException extends RuntimeException {

    public UploadNotVerifiedException(String message) {
        super(message);
    }

    public UploadNotVerifiedException(String message, Throwable cause) {
        super(message, cause);
    }
}

