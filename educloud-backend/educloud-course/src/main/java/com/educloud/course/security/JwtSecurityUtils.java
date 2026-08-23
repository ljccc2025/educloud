package com.educloud.course.security;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.common.security.AuthenticatedUser;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * JWT claims 解析工具：subject=userId 解析、permissions 提取。
 *
 * <p>依据：M03/M05 设计规格（Access Token sub=userId、permissions 为去重字符串数组）。
 * 非数字 subject 视为无效令牌（与 educloud-file 控制器 subjectUserId 语义一致，抛
 * UNAUTHENTICATED BusinessException）；permissions 缺失视为空集，非数组视为无效令牌。</p>
 */
public final class JwtSecurityUtils {

    private JwtSecurityUtils() {
    }

    /** 解析 sub（userId，数字字符串）为 Long；非数字抛 UNAUTHENTICATED。 */
    public static Long userId(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt");
        String subject = jwt.getSubject();
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHENTICATED,
                    "JWT subject must be a numeric userId: " + subject);
        }
    }

    /** 提取 permissions claim（字符串数组）为不可变集合；缺失 → 空集；非数组 → UNAUTHENTICATED。 */
    public static Set<String> permissions(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt");
        Object value = jwt.getClaim("permissions");
        if (value == null) {
            return Set.of();
        }
        if (!(value instanceof Collection<?> collection)) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHENTICATED,
                    "JWT permissions claim must be an array of strings");
        }
        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        for (Object item : collection) {
            if (!(item instanceof String text)) {
                throw new BusinessException(
                        CommonErrorCode.UNAUTHENTICATED,
                        "JWT permissions claim must be an array of strings");
            }
            permissions.add(text);
        }
        return Set.copyOf(permissions);
    }

    public static boolean hasPermission(Jwt jwt, String permission) {
        return permissions(jwt).contains(permission);
    }

    /** 组装 common {@link AuthenticatedUser}（后续任务控制器取当前用户的标准入口）。 */
    public static AuthenticatedUser authenticatedUser(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt");
        Set<String> roles = roles(jwt);
        return new AuthenticatedUser(
                jwt.getSubject(),
                (String) jwt.getClaim("sid"),
                roles,
                permissions(jwt));
    }

    private static Set<String> roles(Jwt jwt) {
        Object value = jwt.getClaim("roles");
        if (!(value instanceof Collection<?> collection)) {
            return Set.of();
        }
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        for (Object item : collection) {
            if (item instanceof String text) {
                roles.add(text);
            }
        }
        return Set.copyOf(roles);
    }
}