package com.educloud.file.storage;

/**
 * 存储访问内部异常：包装 MinIO/IO 等底层异常。
 *
 * <p>任务 3 先以内部异常形式抛出，任务 7 统一错误码时由服务层映射为对外
 * {@code FileErrorCode}，本类届时可替换或保留为内部根异常。</p>
 */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
