package com.educloud.gateway.security;

public enum SessionCheckResult {
    ACTIVE,
    MISSING,
    REVOKED,
    SUBJECT_MISMATCH,
    VERSION_MISMATCH,
    CORRUPT,
    DEPENDENCY_ERROR
}
