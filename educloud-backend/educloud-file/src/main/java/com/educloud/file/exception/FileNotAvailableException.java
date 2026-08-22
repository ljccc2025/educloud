package com.educloud.file.exception;

/**
 * 文件对象存在但状态非 AVAILABLE（如 UPLOADING/QUARANTINED/DELETED），不可绑定。
 *
 * <p>任务 5 内部异常；任务 7 统一映射为 {@code FILE_NOT_FOUND(404)} 或按状态细化。</p>
 */
public class FileNotAvailableException extends RuntimeException {

    public FileNotAvailableException(String message) {
        super(message);
    }

    public FileNotAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
