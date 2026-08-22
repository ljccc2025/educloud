package com.educloud.file.service;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.file.config.FileProperties;
import com.educloud.file.entity.FileBindingEntity;
import com.educloud.file.entity.FileObjectEntity;
import com.educloud.file.exception.FileAccessDeniedException;
import com.educloud.file.exception.GrantPurposeNotAllowedException;
import com.educloud.file.mapper.FileBindingMapper;
import com.educloud.file.mapper.FileObjectMapper;
import com.educloud.file.observability.FileMetrics;
import com.educloud.file.storage.StorageGateway;
import com.educloud.file.support.FileAccessAuditWriter;
import com.educloud.file.support.GrantPurposePolicy;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 内部下载授权服务：单文件 + 有界批量 presigned GET 授权。
 *
 * <p>依据：M04 设计规格 6.2/7.3 节 —— 逐项校验
 * (ownerService, ownerType, ownerId) 精确活跃绑定（unbound_at IS NULL）+ 文件 AVAILABLE 才
 * GRANTED；未绑定/文件不可用 → UNAVAILABLE；任一 owner 伪造（存在绑定行但 owner 不匹配）→
 * 整批 {@link FileAccessDeniedException} 并写 GRANT_BATCH_DENIED 审计；purpose/subject 越权拒绝；
 * TTL = requestedTtl==null ? defaultTtl : min(requestedTtl, maxTtl)；批量 ≤100 且 requestKey 去重。
 * ownerService 由调用方（内部控制器从 clientId）传入，本服务只校验逻辑。</p>
 */
@Service
public class DownloadGrantService {

    /** 批量授权上限（设计规格 6.2 节：items[≤100]）。 */
    public static final int MAX_BATCH_ITEMS = 100;

    private final FileBindingMapper bindingMapper;
    private final FileObjectMapper objectMapper;
    private final StorageGateway storageGateway;
    private final GrantPurposePolicy purposePolicy;
    private final FileAccessAuditWriter auditWriter;
    private final FileProperties properties;
    private final Clock clock;
    private final FileMetrics metrics;

    public DownloadGrantService(
            FileBindingMapper bindingMapper,
            FileObjectMapper objectMapper,
            StorageGateway storageGateway,
            GrantPurposePolicy purposePolicy,
            FileAccessAuditWriter auditWriter,
            FileProperties properties,
            Clock clock,
            FileMetrics metrics) {
        this.bindingMapper = Objects.requireNonNull(bindingMapper, "bindingMapper");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.storageGateway = Objects.requireNonNull(storageGateway, "storageGateway");
        this.purposePolicy = Objects.requireNonNull(purposePolicy, "purposePolicy");
        this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * 单文件下载授权：精确绑定 + AVAILABLE → GRANTED；未绑定/不可用 → UNAVAILABLE；
     * owner 伪造 → {@link FileAccessDeniedException}。
     */
    public GrantResult grantSingle(String ownerService, GrantSingleRequest req) {
        try {
            return doGrantSingle(ownerService, req);
        } catch (GrantPurposeNotAllowedException | FileAccessDeniedException failure) {
            auditWriter.writeGrantSingle(req.fileId(), req.subjectUserId(), false);
            metrics.recordGrantDenied();
            throw failure;
        }
    }

    private GrantResult doGrantSingle(String ownerService, GrantSingleRequest req) {
        purposePolicy.validate(req.subjectType(), req.subjectUserId(), req.purpose());
        Duration ttl = resolveTtl(req.requestedTtl());

        FileBindingEntity binding = findExactBindingOrRejectForgery(
                ownerService, req.ownerType(), req.ownerId(), req.fileId());
        if (binding == null) {
            auditWriter.writeGrantSingle(req.fileId(), req.subjectUserId(), false);
            metrics.recordGrantDenied();
            return new GrantResult(GrantStatus.UNAVAILABLE, null, null);
        }

        FileObjectEntity file = objectMapper.selectById(req.fileId());
        if (file == null || !FileObjectService.STATUS_AVAILABLE.equals(file.getStatus())) {
            auditWriter.writeGrantSingle(req.fileId(), req.subjectUserId(), false);
            metrics.recordGrantDenied();
            return new GrantResult(GrantStatus.UNAVAILABLE, null, null);
        }

        Instant expiresAt = clock.instant().plus(ttl);
        String url = storageGateway.presignedGetUrl(file.getBucket(), file.getObjectKey(), ttl);
        auditWriter.writeGrantSingle(req.fileId(), req.subjectUserId(), true);
        metrics.recordGrantGranted();
        return new GrantResult(GrantStatus.GRANTED, url, expiresAt);
    }

    /**
     * 批量下载授权：先整体校验（purpose/subject/items 数量与 requestKey 去重），
     * 再逐项查绑定；任一 owner 伪造 → 整批 403 + GRANT_BATCH_DENIED 审计。
     */
    public BatchGrantResult grantBatch(String ownerService, GrantBatchRequest req) {
        validateBatchShape(req);
        try {
            purposePolicy.validate(req.subjectType(), req.subjectUserId(), req.purpose());
        } catch (GrantPurposeNotAllowedException e) {
            // 批量 purpose 越权无具体文件可归属：审计 fileId 用 0L 哨兵。
            auditWriter.writeGrantBatchDenied(0L, req.subjectUserId());
            metrics.recordGrantDenied();
            throw e;
        }
        Duration ttl = resolveTtl(req.requestedTtl());

        // 第一遍：全量只做绑定校验（不生成 URL/不审计），任一伪造立即整批 403。
        List<FileBindingEntity> validatedBindings = new ArrayList<>(req.items().size());
        for (BatchItem item : req.items()) {
            try {
                validatedBindings.add(findExactBindingOrRejectForgery(
                        ownerService, item.ownerType(), item.ownerId(), item.fileId()));
            } catch (FileAccessDeniedException e) {
                auditWriter.writeGrantBatchDenied(item.fileId(), req.subjectUserId());
                metrics.recordGrantDenied();
                throw e;
            }
        }
        // 第二遍：全部通过后才生成 URL 与结果。
        List<BatchItemResult> results = new ArrayList<>(req.items().size());
        for (int i = 0; i < req.items().size(); i++) {
            BatchItem item = req.items().get(i);
            FileBindingEntity binding = validatedBindings.get(i);
            if (binding == null) {
                results.add(new BatchItemResult(
                        item.requestKey(), item.fileId(), GrantStatus.UNAVAILABLE, null, null));
                metrics.recordGrantDenied();
                continue;
            }
            FileObjectEntity file = objectMapper.selectById(item.fileId());
            if (file == null || !FileObjectService.STATUS_AVAILABLE.equals(file.getStatus())) {
                results.add(new BatchItemResult(
                        item.requestKey(), item.fileId(), GrantStatus.UNAVAILABLE, null, null));
                metrics.recordGrantDenied();
                continue;
            }
            Instant expiresAt = clock.instant().plus(ttl);
            String url = storageGateway.presignedGetUrl(file.getBucket(), file.getObjectKey(), ttl);
            results.add(new BatchItemResult(
                    item.requestKey(), item.fileId(), GrantStatus.GRANTED, url, expiresAt));
            metrics.recordGrantGranted();
        }
        return new BatchGrantResult(results);
    }

    private void validateBatchShape(GrantBatchRequest req) {
        if (req.items() == null || req.items().isEmpty()) {
            throw new BusinessException(
                    CommonErrorCode.VALIDATION_FAILED, "批量 grant items 不能为空");
        }
        if (req.items().size() > MAX_BATCH_ITEMS) {
            throw new BusinessException(
                    CommonErrorCode.VALIDATION_FAILED,
                    "批量 grant items 超过上限: " + req.items().size() + " > " + MAX_BATCH_ITEMS);
        }
        Set<String> keys = new HashSet<>(req.items().size());
        for (BatchItem item : req.items()) {
            if (item == null) {
                throw new BusinessException(
                        CommonErrorCode.VALIDATION_FAILED, "批量 grant item 不能为 null");
            }
            if (item.requestKey() == null || !keys.add(item.requestKey())) {
                throw new BusinessException(
                        CommonErrorCode.VALIDATION_FAILED,
                        "批量 grant requestKey 缺失或重复: " + item.requestKey());
            }
        }
    }

    /**
     * 精确活跃绑定查询；无精确绑定但文件存在同 (ownerService, ownerType) 的其他活跃绑定
     * （ownerId 不匹配）→ 视为伪造抛 {@link FileAccessDeniedException}；完全无绑定返回 null
     * （调用方映射 UNAVAILABLE）。
     */
    private FileBindingEntity findExactBindingOrRejectForgery(
            String ownerService, String ownerType, String ownerId, Long fileId) {
        FileBindingEntity exact = bindingMapper.findActiveByOwner(
                fileId, ownerService, ownerType, ownerId);
        if (exact != null) {
            return exact;
        }
        List<FileBindingEntity> active = bindingMapper.findActiveByFileId(fileId);
        if (active != null) {
            boolean sameOwnerKey = active.stream().anyMatch(binding ->
                    ownerService.equals(binding.getOwnerService())
                            && ownerType.equals(binding.getOwnerType()));
            if (sameOwnerKey) {
                throw new FileAccessDeniedException(
                        "文件绑定属主不匹配，禁止授权: fileId=" + fileId);
            }
        }
        return null;
    }

    private Duration resolveTtl(Duration requestedTtl) {
        if (requestedTtl != null && requestedTtl.isNegative()) {
            throw new BusinessException(
                    CommonErrorCode.VALIDATION_FAILED, "requestedTtl 不能为负: " + requestedTtl);
        }
        FileProperties.DownloadGrant grant = properties.downloadGrant();
        Duration effective = requestedTtl == null ? grant.defaultTtl() : requestedTtl;
        Duration maxTtl = grant.maxTtl();
        return effective.compareTo(maxTtl) > 0 ? maxTtl : effective;
    }

    /** 单文件授权请求（ownerService 由内部控制器从 clientId 推导，不在请求体内）。 */
    public record GrantSingleRequest(
            String subjectType,
            Long subjectUserId,
            String ownerType,
            String ownerId,
            Long fileId,
            String purpose,
            Duration requestedTtl) {
    }

    /** 单文件授权结果。 */
    public record GrantResult(GrantStatus status, String url, Instant expiresAt) {
    }

    /** 批量授权请求。 */
    public record GrantBatchRequest(
            String subjectType,
            Long subjectUserId,
            String purpose,
            Duration requestedTtl,
            List<BatchItem> items) {
    }

    /** 批量授权单项。 */
    public record BatchItem(String requestKey, Long fileId, String ownerType, String ownerId) {
    }

    /** 批量授权结果。 */
    public record BatchGrantResult(List<BatchItemResult> items) {
    }

    /** 批量授权单项结果。 */
    public record BatchItemResult(
            String requestKey, Long fileId, GrantStatus status, String url, Instant expiresAt) {
    }

    /** 授权状态：GRANTED=已签发 presigned GET；UNAVAILABLE=未绑定或文件不可用。 */
    public enum GrantStatus {
        GRANTED,
        UNAVAILABLE
    }
}
