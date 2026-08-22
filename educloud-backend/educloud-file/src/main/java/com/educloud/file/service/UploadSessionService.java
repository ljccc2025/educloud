package com.educloud.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.educloud.file.config.FileProperties;
import com.educloud.file.dto.request.CreateUploadSessionRequest;
import com.educloud.file.dto.response.UploadSessionResponse;
import com.educloud.file.entity.FileObjectEntity;
import com.educloud.file.entity.FileUploadSessionEntity;
import com.educloud.file.exception.UploadNotVerifiedException;
import com.educloud.file.exception.UploadSessionAccessDeniedException;
import com.educloud.file.exception.UploadSessionExpiredException;
import com.educloud.file.exception.UploadSessionNotFoundException;
import com.educloud.file.exception.UploadSessionStateException;
import com.educloud.file.mapper.FileObjectMapper;
import com.educloud.file.mapper.FileUploadSessionMapper;
import com.educloud.file.storage.FileTooLargeException;
import com.educloud.file.storage.FileTypeNotAllowedException;
import com.educloud.file.storage.StorageGateway;
import com.educloud.file.support.ContentTypePolicy;
import com.educloud.file.support.ObjectKeyFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 上传会话状态机：create（PENDING + presigned PUT）→ complete（PENDING→COMPLETED，
 * 落 AVAILABLE 对象）→ 过期 EXPIRED。
 *
 * <p>依据：M04 设计规格第 7.1 节（上传流程）与第 5 节（表结构）。状态枚举值存 String：
 * PENDING/COMPLETED/EXPIRED/ABORTED（V001 注释）；本任务不发布事件（任务 5/11 做 Outbox）。
 * 内部异常（UploadNotVerified/UploadSessionExpired 等）由任务 7 统一映射对外错误码。</p>
 */
@Service
public class UploadSessionService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_ABORTED = "ABORTED";

    private final FileUploadSessionMapper sessionMapper;
    private final FileObjectMapper objectMapper;
    private final StorageGateway storageGateway;
    private final ObjectKeyFactory objectKeyFactory;
    private final ContentTypePolicy contentTypePolicy;
    private final FileProperties properties;
    private final Clock clock;

    public UploadSessionService(
            FileUploadSessionMapper sessionMapper,
            FileObjectMapper objectMapper,
            StorageGateway storageGateway,
            ObjectKeyFactory objectKeyFactory,
            ContentTypePolicy contentTypePolicy,
            FileProperties properties,
            Clock clock) {
        this.sessionMapper = Objects.requireNonNull(sessionMapper, "sessionMapper");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.storageGateway = Objects.requireNonNull(storageGateway, "storageGateway");
        this.objectKeyFactory = Objects.requireNonNull(objectKeyFactory, "objectKeyFactory");
        this.contentTypePolicy = Objects.requireNonNull(contentTypePolicy, "contentTypePolicy");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 创建上传会话：校验类型/大小 → 生成对象键 → presigned PUT URL → 落库 PENDING。
     *
     * <p>expectedSizeBytes 为空则跳过大小预检（complete 时以实际 stat 为准）。
     * put URL TTL 与会话 TTL 来自 {@link FileProperties#upload()}。</p>
     */
    public UploadSessionResponse create(Long uploaderId, CreateUploadSessionRequest req) {
        contentTypePolicy.validate(req.contentType(), req.expectedSizeBytes());

        FileProperties.Upload upload = properties.upload();
        String bucket = properties.storage().bucket();
        String objectKey = objectKeyFactory.create("user-" + uploaderId, req.contentType());
        String uploadUrl = storageGateway.presignedPutUrl(
                bucket, objectKey, req.contentType(), upload.putUrlTtl());

        Instant now = clock.instant();
        FileUploadSessionEntity session = new FileUploadSessionEntity();
        session.setId(IdWorker.getId());
        session.setUploaderId(uploaderId);
        session.setObjectKey(objectKey);
        session.setBucket(bucket);
        session.setOriginalName(req.originalName() == null ? "" : req.originalName());
        session.setContentType(req.contentType());
        session.setExpectedSizeBytes(req.expectedSizeBytes());
        session.setStatus(STATUS_PENDING);
        session.setPutUrlExpiresAt(now.plus(upload.putUrlTtl()));
        session.setExpiresAt(now.plus(upload.sessionTtl()));
        session.setCreatedAt(now);
        session.setVersion(1);
        sessionMapper.insert(session);

        return new UploadSessionResponse(session.getId(), uploadUrl, upload.putUrlTtl().toSeconds());
    }

    /**
     * 确认上传完成：锁会话行 → 校验归属/状态/过期 → stat 存在与大小/类型 → SHA-256 →
     * 落 file_object(AVAILABLE) → 会话 COMPLETED。
     *
     * <p>过期会话先置 EXPIRED 再拒绝（EXPIRED 更新不回滚），其余失败不产生任何写入。</p>
     */
    @Transactional(noRollbackFor = UploadSessionExpiredException.class)
    public FileObjectEntity complete(Long uploaderId, Long sessionId) {
        FileUploadSessionEntity session = sessionMapper.selectByIdForUpdate(sessionId);
        if (session == null) {
            throw new UploadSessionNotFoundException("上传会话不存在: sessionId=" + sessionId);
        }
        if (!Objects.equals(session.getUploaderId(), uploaderId)) {
            throw new UploadSessionAccessDeniedException(
                    "会话归属校验失败: sessionId=" + sessionId + ", uploaderId=" + uploaderId);
        }
        if (STATUS_EXPIRED.equals(session.getStatus())) {
            throw new UploadSessionExpiredException("上传会话已过期: sessionId=" + sessionId);
        }
        if (!STATUS_PENDING.equals(session.getStatus())) {
            throw new UploadSessionStateException(
                    "会话状态不允许 complete: sessionId=" + sessionId + ", status=" + session.getStatus());
        }
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(clock.instant())) {
            session.setStatus(STATUS_EXPIRED);
            sessionMapper.updateById(session);
            throw new UploadSessionExpiredException("上传会话已过期: sessionId=" + sessionId);
        }

        String bucket = session.getBucket();
        String objectKey = session.getObjectKey();
        StorageGateway.ObjectStat stat = storageGateway.stat(bucket, objectKey);
        if (!stat.exists()) {
            throw new UploadNotVerifiedException(
                    "对象存储中不存在目标对象: bucket=" + bucket + ", objectKey=" + objectKey);
        }

        FileProperties.Upload upload = properties.upload();
        long maxSizeBytes = upload.maxSizeBytes();
        if (stat.size() > maxSizeBytes) {
            throw new FileTooLargeException("对象实际大小超过上限: " + stat.size() + " > " + maxSizeBytes);
        }
        if (stat.contentType() != null && !contentTypePolicy.isAllowed(stat.contentType())) {
            throw new FileTypeNotAllowedException(
                    "对象实际 Content-Type 不在白名单: " + stat.contentType());
        }

        String sha256 = storageGateway.sha256(bucket, objectKey, Math.toIntExact(maxSizeBytes));
        Instant now = clock.instant();

        FileObjectEntity object = new FileObjectEntity();
        object.setId(IdWorker.getId());
        object.setObjectKey(objectKey);
        object.setOriginalName(session.getOriginalName());
        object.setContentType(session.getContentType());
        object.setSizeBytes(stat.size());
        object.setSha256(sha256);
        object.setBucket(bucket);
        object.setStatus("AVAILABLE");
        object.setUploaderId(session.getUploaderId());
        object.setUploadedAt(now);
        object.setVersion(1);
        objectMapper.insert(object);

        session.setStatus(STATUS_COMPLETED);
        sessionMapper.updateById(session);
        return object;
    }

    /**
     * 批量过期：把超过 maxAge（按 created_at 计龄）的 PENDING 会话置 EXPIRED。
     *
     * <p>供任务 12 清理任务调用；幂等（仅处理 PENDING 行）。</p>
     */
    public void expireOverdue(Duration maxAge) {
        Instant cutoff = clock.instant().minus(maxAge);
        List<FileUploadSessionEntity> overdue = sessionMapper.selectList(
                new LambdaQueryWrapper<FileUploadSessionEntity>()
                        .eq(FileUploadSessionEntity::getStatus, STATUS_PENDING)
                        .lt(FileUploadSessionEntity::getCreatedAt, cutoff));
        for (FileUploadSessionEntity session : overdue) {
            session.setStatus(STATUS_EXPIRED);
            sessionMapper.updateById(session);
        }
    }
}

