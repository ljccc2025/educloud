package com.educloud.file.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建上传会话请求体（POST /api/v1/file-upload-sessions）。
 *
 * <p>依据：M04 设计规格 6.1 节 —— contentType 必填，expectedSizeBytes/originalName 可选；
 * originalName 仅存元数据、不参与对象键拼接（安全文档 11 节）。</p>
 */
public record CreateUploadSessionRequest(
        @NotBlank String contentType,
        Long expectedSizeBytes,
        String originalName) {
}
