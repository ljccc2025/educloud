package com.educloud.file.storage;

/**
 * Content-Type 不在白名单（上传预检或 complete 二次校验拒绝）。
 *
 * <p>任务 4 先定义为内部异常（{@link FileStorageException} 子类，与 FileTooLargeException
 * 同构），任务 7 统一错误码时转换为对外 {@code FILE_TYPE_NOT_ALLOWED(415)}。</p>
 */
public class FileTypeNotAllowedException extends FileStorageException {

    public FileTypeNotAllowedException(String message) {
        super(message);
    }

    public FileTypeNotAllowedException(String message, Throwable cause) {
        super(message, cause);
    }
}

