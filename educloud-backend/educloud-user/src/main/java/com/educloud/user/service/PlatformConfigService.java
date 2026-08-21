package com.educloud.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.user.dto.request.PlatformConfigUpdateRequest;
import com.educloud.user.dto.response.PlatformConfigResponse;
import com.educloud.user.entity.PlatformPublicConfigEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.PlatformPublicConfigMapper;
import com.educloud.user.support.AuditWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 平台公开配置。依据：API 规范第 7 节（匿名读、platform:config:update 写）；
 * 仅非敏感配置（Secret 无产品侧更新 API，安全设计第 9 节）。
 */
@Service
public final class PlatformConfigService {

    private final PlatformPublicConfigMapper mapper;
    private final AuditWriter auditWriter;

    public PlatformConfigService(PlatformPublicConfigMapper mapper, AuditWriter auditWriter) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter");
    }

    public List<PlatformConfigResponse> publicConfigs() {
        return mapper.selectList(new QueryWrapper<PlatformPublicConfigEntity>()
                        .orderByAsc("config_key"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PlatformConfigResponse update(
            PlatformConfigUpdateRequest request,
            String actorId,
            String ip,
            String userAgent,
            String requestId) {
        PlatformPublicConfigEntity config = mapper.selectOne(
                new QueryWrapper<PlatformPublicConfigEntity>()
                        .eq("config_key", request.configKey().trim()));
        if (config == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND, "Unknown platform config key");
        }
        config.setConfigValue(request.configValue());
        config.setValueType(request.valueType());
        config.setDescription(request.description());
        config.setVersion(config.getVersion() + 1);
        config.setUpdatedAt(Instant.now());
        mapper.updateById(config);
        auditWriter.write(new AuditWriter.AuditEntry(
                "USER", actorId == null ? "unknown" : actorId, null,
                "PLATFORM_CONFIG_UPDATED", "platform_public_config", request.configKey().trim(),
                "SUCCESS", null, null, null, ip, userAgent, requestId, null, "ADMIN"));
        return toResponse(config);
    }

    private PlatformConfigResponse toResponse(PlatformPublicConfigEntity config) {
        return new PlatformConfigResponse(
                config.getConfigKey(),
                config.getConfigValue(),
                config.getValueType(),
                config.getDescription(),
                config.getVersion());
    }
}
