package com.educloud.file.security;

import com.educloud.file.config.FileProperties;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * File 服务 JWT claims 校验器（参照 gateway GatewayJwtValidator 精简适配）。
 *
 * <p>用户令牌（aud 含 {@code educloud.file.jwt.audience}，如 educloud-api）执行严格
 * claims 契约：sub/sid/tokenVersion/userType/roles/permissions；服务令牌（aud 含
 * {@code educloud.file.internal.audience}，如 educloud-file）仅做时间/issuer 校验，
 * aud + clientId 白名单由 {@link InternalApiFilter} 自行完成（与 M03 模式一致，
 * 避免 Resource Server 的严格用户契约拒绝合法服务令牌）。</p>
 */
public final class FileJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern AUTHORITY = Pattern.compile("[A-Za-z0-9:._*-]{1,128}");
    private static final Set<String> USER_TYPES = Set.of("STUDENT", "TEACHER", "ADMIN");
    private static final int MAX_AUTHORITIES = 64;
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final String issuer;
    private final String audience;
    private final String internalAudience;
    private final Clock clock;

    public FileJwtValidator(FileProperties properties, Clock clock) {
        this.issuer = properties.jwt().issuer();
        this.audience = properties.jwt().audience();
        this.internalAudience = properties.internal().effectiveInternalAudience();
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Map<String, Object> claims = token.getClaims();
        if (!issuer.equals(claims.get("iss"))) {
            return failure("issuer");
        }
        Object audValue = claims.get("aud");
        if (!(audValue instanceof Collection<?> audiences)
                || !audiences.stream().allMatch(String.class::isInstance)) {
            return failure("audience");
        }
        if (audiences.contains(audience)) {
            if (!hasValidTimestamps(claims)
                    || !isIdentifier(claims.get("sub"))
                    || !isIdentifier(claims.get("sid"))
                    || !isNonNegativeInteger(claims.get("tokenVersion"))
                    || !(claims.get("userType") instanceof String userType)
                    || !USER_TYPES.contains(userType)
                    || !isAuthorityCollection(claims.get("roles"))
                    || !isAuthorityCollection(claims.get("permissions"))) {
                return failure("claims");
            }
            return OAuth2TokenValidatorResult.success();
        }
        if (audiences.contains(internalAudience)) {
            // 服务令牌：由 InternalApiFilter 校验 aud + clientId 白名单。
            return OAuth2TokenValidatorResult.success();
        }
        return failure("audience");
    }

    private boolean hasValidTimestamps(Map<String, Object> claims) {
        Instant expiration = instant(claims.get("exp"));
        Instant notBefore = instant(claims.get("nbf"));
        Instant issuedAt = instant(claims.get("iat"));
        if (expiration == null || notBefore == null || issuedAt == null) {
            return false;
        }
        Instant now = clock.instant();
        return !expiration.isBefore(now.minus(CLOCK_SKEW))
                && !notBefore.isAfter(now.plus(CLOCK_SKEW))
                && !issuedAt.isAfter(now.plus(CLOCK_SKEW));
    }

    private static Instant instant(Object value) {
        return value instanceof Instant instant ? instant : null;
    }

    private static boolean isIdentifier(Object value) {
        return value instanceof String text && IDENTIFIER.matcher(text).matches();
    }

    private static boolean isNonNegativeInteger(Object value) {
        if (value instanceof Byte number) {
            return number >= 0;
        }
        if (value instanceof Short number) {
            return number >= 0;
        }
        if (value instanceof Integer number) {
            return number >= 0;
        }
        if (value instanceof Long number) {
            return number >= 0;
        }
        return value instanceof BigInteger number && number.signum() >= 0;
    }

    private static boolean isAuthorityCollection(Object value) {
        if (value == null) {
            return true;
        }
        if (!(value instanceof Collection<?> authorities) || authorities.size() > MAX_AUTHORITIES) {
            return false;
        }
        Set<String> unique = new HashSet<>();
        for (Object authority : authorities) {
            if (!(authority instanceof String text)
                    || !AUTHORITY.matcher(text).matches()
                    || !unique.add(text)) {
                return false;
            }
        }
        return true;
    }

    private static OAuth2TokenValidatorResult failure(String category) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", category, null));
    }
}
