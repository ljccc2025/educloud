package com.educloud.content.exception;

import com.educloud.common.error.ErrorCode;

public enum ContentErrorCode implements ErrorCode {
    CONTENT_NOT_FOUND(404, "Course content not found"),
    REVISION_NOT_FOUND(404, "Content revision not found"),
    REVISION_NOT_DRAFT(409, "Content revision is not in draft state"),
    CHAPTER_NOT_FOUND(404, "Chapter not found"),
    COURSEWARE_NOT_FOUND(404, "Courseware not found"),
    COURSEWARE_ACCESS_DENIED(403, "Access to courseware is denied"),
    SUBMISSION_NOT_PENDING(409, "Content audit submission is not in pending state"),
    AUDIT_REJECT_REASON_REQUIRED(400, "Reject reason is required"),
    INVALID_PROGRESS(400, "Invalid learning progress report"),
    TEACHER_ACCESS_DENIED(403, "You are not authorized to manage content for this course");

    private final int httpStatus;
    private final String defaultMessage;

    ContentErrorCode(int httpStatus, String defaultMessage) {
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
