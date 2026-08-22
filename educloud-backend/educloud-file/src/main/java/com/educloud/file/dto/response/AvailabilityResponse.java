package com.educloud.file.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 内部文件可用性响应（GET /internal/v1/files/{id}/availability）。
 *
 * <p>exists=false 时仅返回 exists（status/contentType/sizeBytes 为 null 不序列化，
 * 避免暴露“曾存在但已删除”的元数据残留）。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AvailabilityResponse(
        boolean exists,
        String status,
        String contentType,
        Long sizeBytes) {
}
