package com.educloud.file.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 绑定请求体（POST /internal/v1/files/{id}/bind）。
 *
 * <p>ownerService 不由客户端提供，恒由已认证 clientId 推导（规格 6.2 节）。
 * uploaderUserId 为可选的委托上传者（M05 任务 12）：非用户类属主（如课程封面，
 * 属主=课程、上传者=教师）由调用方声明上传者，File 对照 file_object.uploader_id
 * 校验上传者属主（规格 §9 信任边界）；不传时维持 M04 语义（用户类属主按
 * ownerId==uploaderId 校验）。</p>
 */
public record BindRequest(
        @NotBlank @Size(max = 64) String ownerType,
        @NotBlank @Size(max = 128) String ownerId,
        Long uploaderUserId) {
}
