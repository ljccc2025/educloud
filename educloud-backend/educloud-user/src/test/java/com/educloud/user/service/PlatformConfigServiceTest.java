package com.educloud.user.service;

import com.educloud.common.error.BusinessException;
import com.educloud.user.dto.request.PlatformConfigUpdateRequest;
import com.educloud.user.dto.response.PlatformConfigResponse;
import com.educloud.user.entity.PlatformPublicConfigEntity;
import com.educloud.user.mapper.PlatformPublicConfigMapper;
import com.educloud.user.support.AuditWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 平台公开配置单元测试（未知 key 拒绝、更新带版本+审计）。 */
@ExtendWith(MockitoExtension.class)
class PlatformConfigServiceTest {

    @Mock
    private PlatformPublicConfigMapper mapper;
    @Mock
    private AuditWriter auditWriter;

    private PlatformConfigService service;

    @BeforeEach
    void setUp() {
        service = new PlatformConfigService(mapper, auditWriter);
    }

    @Test
    void rejectsUnknownConfigKey() {
        when(mapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.update(
                new PlatformConfigUpdateRequest("secret_key", "x", "STRING", null),
                "1", "ip", "ua", "req-1"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updatesKnownNonSensitiveConfig() {
        PlatformPublicConfigEntity config = new PlatformPublicConfigEntity();
        config.setId(1L);
        config.setConfigKey("site_name");
        config.setConfigValue("EduCloud");
        config.setVersion(0);
        when(mapper.selectOne(any())).thenReturn(config);
        when(mapper.update(org.mockito.ArgumentMatchers.isNull(), any())).thenReturn(1);

        PlatformConfigResponse response = service.update(
                new PlatformConfigUpdateRequest("site_name", "EduCloud Pro", "STRING", "站点名称"),
                "1", "ip", "ua", "req-1");

        assertThat(response.configValue()).isEqualTo("EduCloud Pro");
        assertThat(response.version()).isEqualTo(1);
        verify(auditWriter).write(any(com.educloud.user.support.AuditWriter.AuditEntry.class));
    }
}
