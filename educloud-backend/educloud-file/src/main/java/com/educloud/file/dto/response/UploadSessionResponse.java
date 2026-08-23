package com.educloud.file.dto.response;

/**
 * 创建上传会话响应：sessionId + presigned PUT URL + 有效期（秒）。
 *
 * <p>依据：M04 设计规格 6.1 节 —— {sessionId, uploadUrl, expiresInSeconds}；
 * presigned PUT 有效期默认 5 分钟。</p>
 */
public record UploadSessionResponse(
        // sessionId 为雪花 Long，超出 JS 安全整数（2^53），必须序列化为字符串避免浏览器精度丢失。
        String sessionId,
        String uploadUrl,
        long expiresInSeconds) {
}

