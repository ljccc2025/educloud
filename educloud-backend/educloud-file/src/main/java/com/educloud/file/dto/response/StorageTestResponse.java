package com.educloud.file.dto.response;

/**
 * 存储最小读写探测响应：结果 + 耗时 + 失败类别。
 *
 * <p>依据：M04 设计规格 6.1 节 —— {ok, latencyMs, errorCategory?}；
 * 请求与响应均无密钥。</p>
 */
public record StorageTestResponse(
        boolean ok,
        long latencyMs,
        String errorCategory) {
}
