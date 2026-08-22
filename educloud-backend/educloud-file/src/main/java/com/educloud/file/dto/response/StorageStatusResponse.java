package com.educloud.file.dto.response;

import java.time.Instant;

/**
 * 存储状态响应：脱敏端点标识 + 连通性 + 最近失败类别。
 *
 * <p>依据：M04 设计规格 6.1 节 —— {provider, connected, endpointMasked, checkedAt,
 * lastErrorCategory}；响应绝不包含 accessKey/secretKey。</p>
 */
public record StorageStatusResponse(
        String provider,
        boolean connected,
        String endpointMasked,
        Instant checkedAt,
        String lastErrorCategory) {
}
