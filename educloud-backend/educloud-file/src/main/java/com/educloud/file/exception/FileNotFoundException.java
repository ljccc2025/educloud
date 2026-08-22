package com.educloud.file.exception;

/**
 * 文件对象不存在（file_object 根缺失）。
 *
 * <p>任务 5 内部异常；任务 7 统一映射为 {@code FILE_NOT_FOUND(404)}。</p>
 */
public class FileNotFoundException extends RuntimeException {

    public FileNotFoundException(String message) {
        super(message);
    }

    public FileNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
