package com.educloud.file.dto.response;

import com.educloud.file.entity.FileObjectEntity;

/**
 * 上传确认完成响应：文件对象对外投影。
 *
 * <p>依据：M04 设计规格 6.1 节 —— {fileId, objectKey, sizeBytes, sha256}，
 * 任务 9 补充 contentType 供调用方核对。不含 bucket/uploaderId 等内部字段。</p>
 */
public record FileObjectResponse(
        // fileId 为雪花 Long，超出 JS 安全整数（2^53），必须序列化为字符串避免浏览器精度丢失。
        String fileId,
        String objectKey,
        Long sizeBytes,
        String sha256,
        String contentType) {

    public static FileObjectResponse from(FileObjectEntity entity) {
        return new FileObjectResponse(
                String.valueOf(entity.getId()),
                entity.getObjectKey(),
                entity.getSizeBytes(),
                entity.getSha256(),
                entity.getContentType());
    }
}
