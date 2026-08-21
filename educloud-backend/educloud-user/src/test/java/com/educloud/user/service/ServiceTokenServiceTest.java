package com.educloud.user.service;

import com.educloud.common.error.BusinessException;
import com.educloud.user.config.JwtProperties;
import com.educloud.user.entity.ServiceClientCredentialEntity;
import com.educloud.user.entity.ServiceClientEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.ServiceClientCredentialMapper;
import com.educloud.user.mapper.ServiceClientMapper;
import com.educloud.user.security.ClaimsFactory;
import com.educloud.user.security.UserJwtEncoder;
import com.educloud.user.support.AuditWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 服务令牌签发单元测试（哈希匹配/aud/scope 白名单/5 分钟）。 */
@ExtendWith(MockitoExtension.class)
class ServiceTokenServiceTest {

    @Mock
    private ServiceClientMapper clientMapper;
    @Mock
    private ServiceClientCredentialMapper credentialMapper;
    @Mock
    private UserJwtEncoder jwtEncoder;
    @Mock
    private AuditWriter auditWriter;

    private ServiceTokenService service;

    @BeforeEach
    void setUp() {
        service = new ServiceTokenService(
                clientMapper,
                credentialMapper,
                new ClaimsFactory(new JwtProperties(
                        "/unused", "https://issuer.educloud.local", "educloud-api", Duration.ofMinutes(5))),
                jwtEncoder,
                new JwtProperties("/unused", "https://issuer.educloud.local", "educloud-api", Duration.ofMinutes(5)),
                auditWriter,
                new ObjectMapper());
    }

    private ServiceClientEntity client() {
        ServiceClientEntity client = new ServiceClientEntity();
        client.setId(1L);
        client.setClientId("order-service");
        client.setStatus("ACTIVE");
        client.setTokenVersion(1L);
        client.setAllowedAudiencesJson("[\"educloud-order\"]");
        client.setAllowedScopesJson("[\"order:read\",\"course:read\"]");
        return client;
    }

    private ServiceClientCredentialEntity credential(String secretHash) {
        ServiceClientCredentialEntity credential = new ServiceClientCredentialEntity();
        credential.setServiceClientId(1L);
        credential.setSecretHash(secretHash);
        credential.setStatus("ACTIVE");
        return credential;
    }

    @Test
    void issuesScopedServiceTokenWithWhiteListedAudience() {
        when(clientMapper.selectOne(any())).thenReturn(client());
        when(credentialMapper.selectOne(any())).thenReturn(
                credential(com.educloud.user.session.SessionFactory.sha256Hex("s3cret")));
        when(jwtEncoder.encode(any())).thenReturn("signed-service-token");

        ServiceTokenService.IssueResult result = service.issue(
                "order-service", "s3cret", "educloud-order", List.of("order:read"), "127.0.0.1", "req-1");

        assertThat(result.accessToken()).isEqualTo("signed-service-token");
        assertThat(result.expiresIn()).isEqualTo(300);
        verify(auditWriter).write(any(AuditWriter.AuditEntry.class));
    }

    @Test
    void rejectsUnknownClientAndWrongSecret() {
        when(clientMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.issue(
                "nope", "s", "educloud-order", List.of("order:read"), "ip", "req"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.SERVICE_CLIENT_NOT_FOUND);

        when(clientMapper.selectOne(any())).thenReturn(client());
        when(credentialMapper.selectOne(any())).thenReturn(
                credential(com.educloud.user.session.SessionFactory.sha256Hex("other")));
        assertThatThrownBy(() -> service.issue(
                "order-service", "wrong", "educloud-order", List.of("order:read"), "ip", "req"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.SERVICE_CREDENTIAL_INVALID);
    }

    @Test
    void rejectsNonWhiteListedAudienceAndScope() {
        when(clientMapper.selectOne(any())).thenReturn(client());
        when(credentialMapper.selectOne(any())).thenReturn(
                credential(com.educloud.user.session.SessionFactory.sha256Hex("s3cret")));

        assertThatThrownBy(() -> service.issue(
                "order-service", "s3cret", "educloud-payment", List.of("order:read"), "ip", "req"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.SERVICE_TOKEN_SCOPE_DENIED);

        assertThatThrownBy(() -> service.issue(
                "order-service", "s3cret", "educloud-order", List.of("payment:write"), "ip", "req"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(UserErrorCode.SERVICE_TOKEN_SCOPE_DENIED);
    }
}
