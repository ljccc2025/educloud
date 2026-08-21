package com.educloud.user.command;

import com.educloud.common.error.BusinessException;
import com.educloud.user.entity.ServiceClientEntity;
import com.educloud.user.mapper.ServiceClientCredentialMapper;
import com.educloud.user.mapper.ServiceClientMapper;
import com.educloud.user.support.AuditWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 服务客户端凭据命令单元测试（幂等 bootstrap、隐式覆盖拒绝、轮换双凭据）。 */
@ExtendWith(MockitoExtension.class)
class ServiceClientCredentialCommandTest {

    @Mock
    private ServiceClientMapper clientMapper;
    @Mock
    private ServiceClientCredentialMapper credentialMapper;
    @Mock
    private AuditWriter auditWriter;

    private ServiceClientCredentialCommand command;

    @BeforeEach
    void setUp() {
        command = new ServiceClientCredentialCommand(clientMapper, credentialMapper, auditWriter, new ObjectMapper());
    }

    @Test
    void bootstrapIsIdempotentForSameSecret() {
        // 第一次查询：client 不存在 -> 创建；第二次查询：client 已存在。
        when(clientMapper.selectOne(any())).thenReturn(null, new ServiceClientEntity());
        when(clientMapper.insert(any(ServiceClientEntity.class))).thenReturn(1);
        when(credentialMapper.insert(any(com.educloud.user.entity.ServiceClientCredentialEntity.class))).thenReturn(1);

        com.educloud.user.entity.ServiceClientCredentialEntity active =
                new com.educloud.user.entity.ServiceClientCredentialEntity();
        active.setSecretHash(com.educloud.user.session.SessionFactory.sha256Hex("same-secret"));
        active.setStatus("ACTIVE");
        when(credentialMapper.selectOne(any())).thenReturn(null, active);

        command.bootstrap("order-service", "same-secret", List.of("educloud-order"), List.of("order:read"), "req");
        command.bootstrap("order-service", "same-secret", List.of("educloud-order"), List.of("order:read"), "req");
        // 第二次幂等：相同 clientId+secret 不产生新写入。
        verify(clientMapper).insert(any(ServiceClientEntity.class));
    }

    @Test
    void bootstrapRefusesImplicitSecretOverride() {
        com.educloud.user.entity.ServiceClientCredentialEntity active =
                new com.educloud.user.entity.ServiceClientCredentialEntity();
        active.setSecretHash(com.educloud.user.session.SessionFactory.sha256Hex("old-secret"));
        active.setStatus("ACTIVE");
        when(clientMapper.selectOne(any())).thenReturn(new ServiceClientEntity());
        when(credentialMapper.selectOne(any())).thenReturn(active);

        assertThatThrownBy(() -> command.bootstrap(
                "order-service", "new-secret", List.of("educloud-order"), List.of("order:read"), "req"))
                .isInstanceOf(BusinessException.class);
        verify(credentialMapper, never()).insert(any(com.educloud.user.entity.ServiceClientCredentialEntity.class));
    }

    @Test
    void rotateKeepsOneActiveAndOneGraceCredential() {
        com.educloud.user.entity.ServiceClientEntity client = new com.educloud.user.entity.ServiceClientEntity();
        client.setId(1L);
        client.setClientId("order-service");
        when(clientMapper.selectOne(any())).thenReturn(client);
        com.educloud.user.entity.ServiceClientCredentialEntity active =
                new com.educloud.user.entity.ServiceClientCredentialEntity();
        active.setId(10L);
        active.setServiceClientId(1L);
        active.setCredentialVersion(1);
        active.setStatus("ACTIVE");
        when(credentialMapper.selectOne(any())).thenReturn(active);

        command.rotate("order-service", "new-secret", "req");

        verify(credentialMapper).updateById(active);
        org.mockito.Mockito.verify(credentialMapper).insert(
                any(com.educloud.user.entity.ServiceClientCredentialEntity.class));
    }
}
