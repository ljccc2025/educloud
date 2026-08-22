package com.educloud.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * `educloud.file` 全部配置。依据：M04 设计规格第 10 节（字段与默认值一一对应，env 可覆盖）。
 */
@Validated
@ConfigurationProperties("educloud.file")
public record FileProperties(
        Storage storage,
        Upload upload,
        DownloadGrant downloadGrant,
        Cleanup cleanup,
        StorageTest storageTest,
        Internal internal,
        Jwt jwt,
        String environment) {

    /**
     * environment 默认 local：与 user SessionProperties 同源（同一 EDUCLOUD_ENVIRONMENT），
     * 用于 Redis key 环境命名空间（storage-tests 限频），避免多环境共享 Redis 互相挤占。
     */
    public FileProperties {
        if (environment == null || environment.isBlank()) {
            environment = "local";
        }
    }

    /** MinIO 存储连接与私有 bucket。密钥不进 API 响应与日志（规格第 9 节）。 */
    public record Storage(
            String endpoint,
            String accessKey,
            String secretKey,
            String bucket) {
    }

    /** 上传限制：大小上限（默认 10MB）、类型白名单、presigned PUT 有效期、会话有效期。 */
    public record Upload(
            long maxSizeBytes,
            List<String> allowedContentTypes,
            Duration putUrlTtl,
            Duration sessionTtl) {
    }

    /** 内部下载授权：默认/最大 TTL 与 purpose 白名单（规格 6.2/10 节）。 */
    public record DownloadGrant(
            Duration defaultTtl,
            Duration maxTtl,
            List<String> purposes) {
    }

    /** 未绑定文件保留期、过期会话时限与每批处理数量（规格 7.4 节）。 */
    public record Cleanup(
            Duration unboundRetention,
            Duration sessionExpiry,
            int batchSize) {
    }

    /** storage-tests 最小读写探测的限频（默认 1 次/分钟/用户，规格 6.1/9 节）。 */
    public record StorageTest(
            int rateLimit,
            Duration window) {
    }

    /** 内部 API 服务令牌：bootstrap 密钥、clientId 白名单与期望 audience（复用 M03 InternalApiFilter 模式）。 */
    public record Internal(
            String bootstrapKey,
            List<String> allowedClientIds,
            String audience) {

        /** 内部接口期望的 audience；未配置时默认 educloud-file（与 user InternalProperties 同构）。 */
        public String effectiveInternalAudience() {
            return audience == null || audience.isBlank() ? "educloud-file" : audience;
        }
    }

    /** 用户令牌验签（Resource Server）：User 公钥 JWKS 位置、issuer/audience（规格 9 节）。 */
    public record Jwt(
            String jwksLocation,
            String issuer,
            String audience) {
    }
}
