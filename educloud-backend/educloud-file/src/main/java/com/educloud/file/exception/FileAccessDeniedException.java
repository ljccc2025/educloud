package com.educloud.file.exception;

/**
 * 文件访问被拒绝：属主伪造/错配（存在绑定行但请求 owner 不匹配）。
 *
 * <p>任务 6 内部异常；任务 7 统一映射为 {@code FILE_ACCESS_DENIED(403)}，
 * 批量接口任一项伪造 → 整批失败并写 GRANT_BATCH_DENIED 审计。</p>
 */
public class FileAccessDeniedException extends RuntimeException {

    public FileAccessDeniedException(String message) {
        super(message);
    }

    public FileAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
