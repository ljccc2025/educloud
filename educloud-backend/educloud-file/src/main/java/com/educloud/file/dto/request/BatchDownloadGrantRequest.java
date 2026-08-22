package com.educloud.file.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量下载授权请求体（POST /internal/v1/file-download-grants/batch）。
 *
 * <p>依据：API 规范 14 节 —— items 去重且最多 100 个；任一伪造/错配 owner 使整批失败
 * （服务层校验，控制器只做请求形状约束）。</p>
 */
public record BatchDownloadGrantRequest(
        @NotBlank String subjectType,
        Long subjectUserId,
        @NotBlank String purpose,
        @PositiveOrZero Long requestedTtlSeconds,
        @NotEmpty @Size(max = 100) List<@Valid Item> items) {

    /** 批量授权单项（与 DownloadGrantService.BatchItem 同构）。 */
    public record Item(
            @NotBlank @Size(max = 64) String requestKey,
            @NotNull Long fileId,
            @NotBlank @Size(max = 64) String ownerType,
            @NotBlank @Size(max = 128) String ownerId) {
    }
}
