package com.educloud.file.exception;

/**
 * grant purpose 越权：未知 purpose、ANONYMOUS 使用非公开 purpose、
 * USER 缺 subjectUserId 或未知 subjectType。
 *
 * <p>任务 6 内部异常；任务 7 统一映射为 {@code GRANT_PURPOSE_NOT_ALLOWED(403)}。</p>
 */
public class GrantPurposeNotAllowedException extends RuntimeException {

    public GrantPurposeNotAllowedException(String message) {
        super(message);
    }

    public GrantPurposeNotAllowedException(String message, Throwable cause) {
        super(message, cause);
    }
}
