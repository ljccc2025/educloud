package com.educloud.user.command;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.user.entity.ServiceClientCredentialEntity;
import com.educloud.user.entity.ServiceClientEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.ServiceClientCredentialMapper;
import com.educloud.user.mapper.ServiceClientMapper;
import com.educloud.user.session.SessionFactory;
import com.educloud.user.support.AuditWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 服务客户端注册与凭据轮换。依据：安全设计第 8 节（secret 只存哈希；相同 clientId+secret
 * 幂等；不同 secret 拒绝隐式覆盖；轮换新 ACTIVE + 旧 GRACE，最多一 ACTIVE 一 GRACE）。
 * 原始 Secret 只从受控渠道（stdin/Secret 文件）进入，不进参数/stdout/日志。
 */
@Service
public final class ServiceClientCredentialCommand {

    private static final java.time.Duration GRACE_DURATION = java.time.Duration.ofHours(24);

    private final ServiceClientMapper clientMapper;
    private final ServiceClientCredentialMapper credentialMapper;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;

    public ServiceClientCredentialCommand(
            ServiceClientMapper clientMapper,
            ServiceClientCredentialMapper credentialMapper,
            AuditWriter auditWriter,
            ObjectMapper objectMapper) {
        this.clientMapper = Objects.requireNonNull(clientMapper, "clientMapper");
        this.credentialMapper = Objects.requireNonNull(credentialMapper, "credentialMapper");
        this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Transactional
    public void bootstrap(
            String clientId, String secret, List<String> audiences, List<String> scopes,
            String requestId) {
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(secret, "secret");
        String secretHash = SessionFactory.sha256Hex(secret);
        ServiceClientEntity client = clientMapper.selectOne(
                new QueryWrapper<ServiceClientEntity>().eq("client_id", clientId));
        if (client == null) {
            Instant now = Instant.now();
            client = new ServiceClientEntity();
            client.setClientId(clientId);
            client.setStatus("ACTIVE");
            client.setAllowedAudiencesJson(json(audiences));
            client.setAllowedScopesJson(json(scopes));
            client.setTokenVersion(0L);
            client.setCreatedAt(now);
            client.setUpdatedAt(now);
            client.setVersion(0);
            clientMapper.insert(client);
            insertCredential(client.getId(), 1, secretHash, "ACTIVE");
        } else {
            ServiceClientCredentialEntity active = activeCredential(client.getId());
            if (active != null) {
                if (active.getSecretHash().equals(secretHash)) {
                    return; // 幂等：相同 clientId + secret 重跑不产生副作用
                }
                throw new BusinessException(
                        UserErrorCode.SERVICE_CLIENT_DISABLED,
                        "Service client already exists with a different secret; use explicit rotation");
            }
            insertCredential(client.getId(), nextVersion(client.getId()), secretHash, "ACTIVE");
        }
        auditWriter.write(new AuditWriter.AuditEntry(
                "SERVICE_BOOTSTRAP_JOB", clientId, null,
                "SERVICE_CLIENT_BOOTSTRAPPED", "service_client", clientId,
                "SUCCESS", null, null, null, null, null, requestId, null, "SECURITY"));
    }

    @Transactional
    public void rotate(String clientId, String newSecret, String requestId) {
        ServiceClientEntity client = clientMapper.selectOne(
                new QueryWrapper<ServiceClientEntity>().eq("client_id", clientId));
        if (client == null) {
            throw new BusinessException(UserErrorCode.SERVICE_CLIENT_NOT_FOUND);
        }
        ServiceClientCredentialEntity active = activeCredential(client.getId());
        if (active == null) {
            insertCredential(client.getId(), nextVersion(client.getId()), SessionFactory.sha256Hex(newSecret), "ACTIVE");
        } else {
            // 旧凭据进入 24h GRACE（宽限期内仍可签发），新凭据 ACTIVE。
            active.setStatus("GRACE");
            credentialMapper.updateById(active);
            insertCredential(client.getId(), active.getCredentialVersion() + 1, SessionFactory.sha256Hex(newSecret), "ACTIVE");
        }
        auditWriter.write(new AuditWriter.AuditEntry(
                "SERVICE_BOOTSTRAP_JOB", clientId, null,
                "SERVICE_CLIENT_ROTATED", "service_client", clientId,
                "SUCCESS", null, null, null, null, null, requestId, null, "SECURITY"));
    }

    private ServiceClientCredentialEntity activeCredential(Long serviceClientId) {
        return credentialMapper.selectOne(new QueryWrapper<ServiceClientCredentialEntity>()
                .eq("service_client_id", serviceClientId)
                .eq("status", "ACTIVE"));
    }

    private int nextVersion(Long serviceClientId) {
        List<ServiceClientCredentialEntity> credentials = credentialMapper.selectList(
                new QueryWrapper<ServiceClientCredentialEntity>()
                        .eq("service_client_id", serviceClientId)
                        .orderByDesc("credential_version"));
        return credentials.isEmpty() ? 1 : credentials.get(0).getCredentialVersion() + 1;
    }

    private void insertCredential(Long serviceClientId, int version, String secretHash, String status) {
        ServiceClientCredentialEntity credential = new ServiceClientCredentialEntity();
        credential.setServiceClientId(serviceClientId);
        credential.setCredentialVersion(version);
        credential.setSecretHash(secretHash);
        credential.setStatus(status);
        credential.setNotBefore(Instant.now());
        if ("GRACE".equals(status)) {
            credential.setExpiresAt(Instant.now().plus(GRACE_DURATION));
        }
        credentialMapper.insert(credential);
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize allowed values", exception);
        }
    }
}
