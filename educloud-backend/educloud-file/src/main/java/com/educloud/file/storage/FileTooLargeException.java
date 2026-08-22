package com.educloud.file.storage;

/**
 * 对象超过限量读取/下载上限。
 *
 * <p>任务 3 先定义为内部异常（{@link FileStorageException} 子类），
 * 任务 7 统一错误码时转换为对外错误响应。</p>
 */
public class FileTooLargeException extends FileStorageException {

    public FileTooLargeException(String message) {
        super(message);
    }

    public FileTooLargeException(String message, Throwable cause) {
        super(message, cause);
    }
}
