package com.educloud.user.security;

import com.educloud.user.config.JwtProperties;
import com.nimbusds.jwt.JWTClaimsSet;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * 组装 Access Token / 服务 Token claims。
 * 依据：M03 设计规格第 4.2 节（Gateway 校验器逐项契约）与第 8 节（服务 Token claims）。
 * permissions 全量去重不得超过 64（Gateway 硬上限），超限拒绝签发（fail-closed）。
 */
@Component
public final class ClaimsFactory {

    private static final int MAX_PERMISSIONS = 64;

    private final JwtProperties properties;

    public ClaimsFactory(JwtProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public JWTClaimsSet userClaims(
            String userId,
            String sessionId,
            String userType,
            long tokenVersion,
            List<String> roles,
            List<String> permissions,
            Instant issuedAt,
            java.time.Duration ttl) {
        Set<String> uniquePermissions = new LinkedHashSet<>(permissions);
        if (uniquePermissions.size() > MAX_PERMISSIONS) {
            throw new IllegalStateException(
                    "User permission set exceeds the Gateway limit of " + MAX_PERMISSIONS);
        }
        Instant now = issuedAt == null ? Instant.now() : issuedAt;
        return new JWTClaimsSet.Builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(userId)
                .claim("sid", sessionId)
                .claim("userType", userType)
                .claim("tokenVersion", tokenVersion)
                .claim("roles", roles)
                .claim("permissions", List.copyOf(uniquePermissions))
                .issueTime(Date.from(now.minusSeconds(1)))
                .notBeforeTime(Date.from(now.minusSeconds(1)))
                .expirationTime(Date.from(now.plus(ttl)))
                .build();
    }

    public JWTClaimsSet serviceClaims(
            String clientId,
            String audience,
            List<String> scopes,
            String jti,
            long tokenVersion,
            Instant issuedAt,
            java.time.Duration ttl) {
        Instant now = issuedAt == null ? Instant.now() : issuedAt;
        return new JWTClaimsSet.Builder()
                .issuer(properties.issuer())
                .audience(List.of(audience))
                .subject("service:" + clientId)
                .claim("clientId", clientId)
                .claim("scope", scopes)
                .jwtID(jti)
                .claim("tokenVersion", tokenVersion)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                .build();
    }
}
