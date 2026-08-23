package com.educloud.file.controller;

import com.educloud.file.dto.request.BatchDownloadGrantRequest;
import com.educloud.file.dto.request.BindRequest;
import com.educloud.file.dto.request.DeleteFileRequest;
import com.educloud.file.dto.request.DownloadGrantRequest;
import com.educloud.file.dto.request.UnbindRequest;
import com.educloud.file.dto.response.AvailabilityResponse;
import com.educloud.file.entity.FileObjectEntity;
import com.educloud.file.exception.FileAccessDeniedException;
import com.educloud.file.mapper.FileObjectMapper;
import com.educloud.file.security.InternalApiFilter;
import com.educloud.file.service.DownloadGrantService;
import com.educloud.file.service.FileBindingService;
import com.educloud.file.service.FileObjectService;
import com.educloud.file.support.OwnerServiceRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 内部文件 API 控制器（/internal/v1/**，仅服务令牌可达）。
 *
 * <p>依据：M04 设计规格 6.2/9 节 —— 内部接口不经过 @PreAuthorize，身份由
 * {@link InternalApiFilter} 校验并把 clientId 写入 request attribute；本控制器所有方法
 * 先 {@link InternalApiFilter#requireClientId} 再由 {@link OwnerServiceRegistry} 推导
 * ownerService（未知 clientId → 403 FILE_ACCESS_DENIED）。ownerService 恒由服务身份
 * 推导，绝不接受客户端输入，绑定/删除/授权均按推导出的 ownerService 校验归属。</p>
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalFileController {

    private final OwnerServiceRegistry ownerServices;
    private final FileBindingService bindingService;
    private final FileObjectService fileObjectService;
    private final DownloadGrantService downloadGrantService;
    private final FileObjectMapper objectMapper;

    public InternalFileController(
            OwnerServiceRegistry ownerServices,
            FileBindingService bindingService,
            FileObjectService fileObjectService,
            DownloadGrantService downloadGrantService,
            FileObjectMapper objectMapper) {
        this.ownerServices = Objects.requireNonNull(ownerServices, "ownerServices");
        this.bindingService = Objects.requireNonNull(bindingService, "bindingService");
        this.fileObjectService = Objects.requireNonNull(fileObjectService, "fileObjectService");
        this.downloadGrantService = Objects.requireNonNull(downloadGrantService, "downloadGrantService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /** 文件可用性快照：调用方对该文件有活跃绑定才可见（无绑定 → 403）。 */
    @GetMapping("/files/{id}/availability")
    public AvailabilityResponse availability(@PathVariable Long id, HttpServletRequest request) {
        String ownerService = ownerService(request);
        if (!bindingService.hasActiveBindingByOwnerService(id, ownerService)) {
            throw new FileAccessDeniedException(
                    "调用方对文件无活跃绑定: fileId=" + id + ", ownerService=" + ownerService);
        }
        FileObjectEntity file = objectMapper.selectById(id);
        if (file == null) {
            return new AvailabilityResponse(false, null, null, null);
        }
        return new AvailabilityResponse(
                true, file.getStatus(), file.getContentType(), file.getSizeBytes());
    }

    /** 绑定文件到业务属主（ownerService 由 clientId 推导）。 */
    @PostMapping("/files/{id}/bind")
    public Map<String, String> bind(
            @PathVariable Long id,
            @Valid @RequestBody BindRequest request,
            HttpServletRequest servletRequest) {
        String ownerService = ownerService(servletRequest);
        bindingService.bind(id, ownerService, request.ownerType(), request.ownerId(),
                request.uploaderUserId());
        return Map.of("status", "BOUND");
    }

    /** 解绑文件与业务属主（幂等）。 */
    @PostMapping("/files/{id}/unbind")
    public Map<String, String> unbind(
            @PathVariable Long id,
            @Valid @RequestBody UnbindRequest request,
            HttpServletRequest servletRequest) {
        String ownerService = ownerService(servletRequest);
        bindingService.unbind(id, ownerService, request.ownerType(), request.ownerId());
        return Map.of("status", "UNBOUND");
    }

    /** 单文件短期下载授权（ownerService 由 clientId 推导）。 */
    @PostMapping("/files/{id}/download-grants")
    public DownloadGrantService.GrantResult downloadGrant(
            @PathVariable Long id,
            @Valid @RequestBody DownloadGrantRequest request,
            HttpServletRequest servletRequest) {
        String ownerService = ownerService(servletRequest);
        return downloadGrantService.grantSingle(ownerService,
                new DownloadGrantService.GrantSingleRequest(
                        request.subjectType(),
                        request.subjectUserId(),
                        request.ownerType(),
                        request.ownerId(),
                        id,
                        request.purpose(),
                        toDuration(request.requestedTtlSeconds())));
    }

    /** 批量短期下载授权（≤100 项，任一项伪造 → 整批 403）。 */
    @PostMapping("/file-download-grants/batch")
    public DownloadGrantService.BatchGrantResult batchGrant(
            @Valid @RequestBody BatchDownloadGrantRequest request,
            HttpServletRequest servletRequest) {
        String ownerService = ownerService(servletRequest);
        List<DownloadGrantService.BatchItem> items = request.items().stream()
                .map(item -> new DownloadGrantService.BatchItem(
                        item.requestKey(), item.fileId(), item.ownerType(), item.ownerId()))
                .toList();
        return downloadGrantService.grantBatch(ownerService,
                new DownloadGrantService.GrantBatchRequest(
                        request.subjectType(),
                        request.subjectUserId(),
                        request.purpose(),
                        toDuration(request.requestedTtlSeconds()),
                        items));
    }

    /** 删除文件：调用方须有绑定记录（曾绑定）；活跃绑定仍存在则拒绝。 */
    @PostMapping("/files/{id}/delete")
    public Map<String, String> delete(
            @PathVariable Long id,
            @Valid @RequestBody DeleteFileRequest request,
            HttpServletRequest servletRequest) {
        String ownerService = ownerService(servletRequest);
        fileObjectService.deleteIfUnbound(
                id, ownerService, request.ownerType(), request.ownerId(), request.reason());
        return Map.of("status", "DELETED");
    }

    private String ownerService(HttpServletRequest request) {
        return ownerServices.require(InternalApiFilter.requireClientId(request));
    }

    private static Duration toDuration(Long requestedTtlSeconds) {
        return requestedTtlSeconds == null ? null : Duration.ofSeconds(requestedTtlSeconds);
    }
}
