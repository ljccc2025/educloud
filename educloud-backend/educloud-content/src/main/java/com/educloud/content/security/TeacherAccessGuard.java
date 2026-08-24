package com.educloud.content.security;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.content.exception.ContentErrorCode;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class TeacherAccessGuard {

    public Long checkTeacherAccess(Jwt jwt) {
        if (jwt == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED, "Authentication required");
        }
        Long userId = JwtSecurityUtils.userId(jwt);
        Set<String> roles = JwtSecurityUtils.roles(jwt);
        Set<String> permissions = JwtSecurityUtils.permissions(jwt);

        boolean isTeacherOrAdmin = roles.contains("TEACHER") || roles.contains("SYSTEM_ADMIN") || roles.contains("SUPER_ADMIN");
        boolean hasManagePerm = permissions.contains("content:manage") || permissions.contains("course:update") || permissions.contains("course:create");

        if (!isTeacherOrAdmin && !hasManagePerm) {
            throw new BusinessException(ContentErrorCode.TEACHER_ACCESS_DENIED, "Teacher permission required");
        }
        return userId;
    }
}
