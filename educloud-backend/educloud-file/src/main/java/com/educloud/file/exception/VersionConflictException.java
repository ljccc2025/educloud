package com.educloud.file.exception;

/**
 * 乐观锁版本冲突：updateById 未命中预期版本（0 行受影响），说明根对象已被并发修改。
 *
 * <p>修复 M1：拦截器以实体当前 version 作为 WHERE 旧值并自动 version+1，
 * 返回值非 1 即并发冲突。内部异常；任务 7 统一映射（如 {@code CONFLICT(409)}）。</p>
 */
public class VersionConflictException extends RuntimeException {

    public VersionConflictException(String message) {
        super(message);
    }

    public VersionConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
