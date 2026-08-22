package com.educloud.file.exception;

/**
 * 文件存在未解绑的活跃绑定（file_binding.unbound_at IS NULL），禁止删除。
 *
 * <p>任务 5 内部异常；任务 7 统一映射为 {@code FILE_BOUND(409)}。</p>
 */
public class FileBoundException extends RuntimeException {

    public FileBoundException(String message) {
        super(message);
    }

    public FileBoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
