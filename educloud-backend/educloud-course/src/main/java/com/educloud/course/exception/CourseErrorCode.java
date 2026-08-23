package com.educloud.course.exception;

import com.educloud.common.error.ErrorCode;

/**
 * Course 服务域错误码。依据：M05 设计规格 §6 错误码表。
 *
 * <p>通用错误（校验/版本冲突/限流/依赖不可用）复用 CommonErrorCode；本枚举只承载
 * Course 域专属语义。code() 返回枚举名，与 API 规范第 4 节错误码命名一致。</p>
 */
public enum CourseErrorCode implements ErrorCode {

    COURSE_NOT_FOUND(404, "Course not found"),
    COURSE_NOT_FREE(409, "Course is not free"),
    COURSE_OFFLINE_OR_ARCHIVED(409, "Course is offline or archived"),
    VERSION_NOT_DRAFT(409, "Course version is not in draft state"),
    SUBMISSION_NOT_PENDING(409, "Audit submission is not in pending state"),
    REVIEW_REJECT_REASON_REQUIRED(400, "Reject reason is required"),
    NOT_ENROLLED(403, "Student is not enrolled in the course"),
    COURSE_ACCESS_DENIED(403, "Course access denied"),
    REVIEW_NOT_FOUND(404, "Course review not found");

    private final int httpStatus;
    private final String defaultMessage;

    CourseErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
