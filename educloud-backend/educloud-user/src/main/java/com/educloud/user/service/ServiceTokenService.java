package com.educloud.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.user.config.JwtProperties;
import com.educloud.user.entity.ServiceClientCredentialEntity;
import com.educloud.user.entity.ServiceClientEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.ServiceClientCredentialMapper;
import com.educloud.user.mapper.ServiceClientMapper;
import com.educloud.user.security.ClaimsFactory;
import com.educloud.user.security.UserJwtEncoder;
import com.educloud.user.session.SessionFactory;
import com.educloud.user.support.AuditWriter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 服务令牌签发。依据：安全设计第 8 节（HTTP Basic client_credentials；secret 哈希匹配；
 * audience/scope 白名单；5 分钟服务 Token；claims 含 sub=service:&lt;clientId&gt;、clientId、aud、
 * scope、jti、tokenVersion）。Token 响应只含 access_token/token_type/expires_in。
 */
@Service
public final class ServiceTokenService {

    private final ServiceClientMapper clientMapper;
    private final ServiceClientCredentialMapper credentialMapper;
    private final ClaimsFactory claimsFactory;
    private final UserJwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;

    public ServiceTokenService(
            ServiceClientMapper clientMapper,
            ServiceClientCredentialMapper credentialMapper,
            ClaimsFactory claimsFactory,
            UserJwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            AuditWriter auditWriter,
            ObjectMapper objectMapper) {
        this.clientMapper = Objects.requireNonNull(clientMapper, "clientMapper");
        this.credentialMapper = Objects.requireNonNull(credentialMapper, "credentialMapper");
        this.claimsFactory = Objects.requireNonNull(claimsFactory, "claimsFactory");
        this.jwtEncoder = Objects.requireNonNull(jwtEncoder, "jwtEncoder");
        this.jwtProperties = Objects.requireNonNull(jwtProperties, "jwtProperties");
        this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public IssueResult issue(
            String clientId, String clientSecret, String audience, List<String> scopes,
            String ip, String requestId) {
        ServiceClientEntity client = clientMapper.selectOne(
                new QueryWrapper<ServiceClientEntity>().eq("client_id", clientId));
        if (client == null) {
            throw new BusinessException(UserErrorCode.SERVICE_CLIENT_NOT_FOUND);
        }
        if (!"ACTIVE".equals(client.getStatus())) {
            throw new BusinessException(UserErrorCode.SERVICE_CLIENT_DISABLED);
        }
        ServiceClientCredentialEntity credential = credentialMapper.selectOne(
                new QueryWrapper<ServiceClientCredentialEntity>()
                        .eq("service_client_id", client.getId())
                        .and(wrapper -> wrapper.eq("status", "ACTIVE")
                                .or().eq("status", "GRACE")));
        if (credential == null
                || !credential.getSecretHash().equals(SessionFactory.sha256Hex(clientSecret))) {
            throw new BusinessException(UserErrorCode.SERVICE_CREDENTIAL_INVALID);
        }
        Set<String> allowedAudiences = new HashSet<>(readList(client.getAllowedAudiencesJson()));
        if (audience == null || !allowedAudiences.contains(audience)) {
            throw new BusinessException(UserErrorCode.SERVICE_TOKEN_SCOPE_DENIED);
        }
        Set<String> allowedScopes = new HashSet<>(readList(client.getAllowedScopesJson()));
        if (scopes == null || !allowedScopes.containsAll(scopes)) {
            throw new BusinessException(UserErrorCode.SERVICE_TOKEN_SCOPE_DENIED);
        }

        Instant now = Instant.now();
        String token = jwtEncoder.encode(claimsFactory.serviceClaims(
                clientId, audience, scopes, UUID.randomUUID().toString(), client.getTokenVersion(),
                now, jwtProperties.serviceTokenTtl()));

        auditWriter.write(new AuditWriter.AuditEntry(
                "SERVICE_CLIENT", clientId, null,
                "SERVICE_TOKEN_ISSUED", "service_client", clientId,
                "SUCCESS", audience, null, null, ip, null, requestId, null, "SECURITY"));

        return new IssueResult(token, jwtProperties.serviceTokenTtl().getSeconds());
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Service client allowed values are malformed", exception);
        }
    }

    public record IssueResult(String accessToken, long expiresIn) {
    }
}
