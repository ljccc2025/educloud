package com.educloud.file.service;

import com.educloud.file.entity.FileObjectEntity;
import com.educloud.file.exception.FileAccessDeniedException;
import com.educloud.file.exception.FileBoundException;
import com.educloud.file.exception.VersionConflictException;
import com.educloud.file.mapper.FileBindingMapper;
import com.educloud.file.mapper.FileObjectMapper;
import com.educloud.file.messaging.FileEventPublisher;
import com.educloud.file.storage.StorageGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

/**
 * 文件对象服务：completeUpload 落对象（委托上传会话状态机）、删除未绑定文件（锁根+判活）。
 *
 * <p>依据：M04 设计规格第 7.2/7.4 节与第 5 节 —— 删除在事务内先 SELECT ... FOR UPDATE
 * 锁 file_object 根并递增 version；活跃绑定（unbound_at IS NULL）存在则拒绝删除；
 * MinIO 对象删除失败时异常上抛、事务回滚（DB 为准）。成功路径经
 * {@link FileEventPublisher} 发布 FileUploaded/FileDeleted（Outbox，事务内写入）。</p>
 */
@Service
public class FileObjectService {

    public static final String STATUS_UPLOADING = "UPLOADING";
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_QUARANTINED = "QUARANTINED";
    public static final String STATUS_DELETED = "DELETED";

    private final UploadSessionService uploadSessionService;
    private final FileObjectMapper objectMapper;
    private final FileBindingMapper bindingMapper;
    private final StorageGateway storageGateway;
    private final FileEventPublisher eventPublisher;
    private final Clock clock;

    public FileObjectService(
            UploadSessionService uploadSessionService,
            FileObjectMapper objectMapper,
            FileBindingMapper bindingMapper,
            StorageGateway storageGateway,
            FileEventPublisher eventPublisher,
            Clock clock) {
        this.uploadSessionService = Objects.requireNonNull(uploadSessionService, "uploadSessionService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.bindingMapper = Objects.requireNonNull(bindingMapper, "bindingMapper");
        this.storageGateway = Objects.requireNonNull(storageGateway, "storageGateway");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 确认上传完成：委托 {@link UploadSessionService#complete} 将对象落为 AVAILABLE，
     * 成功后发布 FileUploaded（Outbox，同一事务内写入；ownerService=null 表示尚未绑定）。
     */
    @Transactional
    public FileObjectEntity completeUpload(Long uploaderId, Long sessionId) {
        FileObjectEntity object = uploadSessionService.complete(uploaderId, sessionId);
        eventPublisher.fileUploaded(
                object.getId(), object.getObjectKey(), null, object.getUploaderId(),
                object.getVersion());
        return object;
    }

    /**
     * 删除文件对象：锁根 → 校验调用方（ownerService/ownerType/ownerId）对该文件有绑定记录
     * （无则 {@link FileAccessDeniedException}）→ 判活跃绑定（有则 {@link FileBoundException}）→
     * 删 MinIO 对象 → 行置 DELETED + deleted_at，根 version+1（乐观锁拦截器生成 WHERE version=旧）。
     *
     * <p>owner 校验用“曾绑定”（含历史解绑行）证明归属，随后仍要求文件当前无任何活跃
     * 绑定才删除；文件不存在按幂等返回（任务 5 允许二选一，本实现选择幂等：调用方无需区分
     * “已删除”与“从未存在”）。MinIO 删除失败时异常上抛，DB 事务回滚，以 DB 为准；
     * reason 保留供任务 11/12 审计与事件载荷使用。</p>
     */
    @Transactional
    public void deleteIfUnbound(
            Long fileId, String ownerService, String ownerType, String ownerId, String reason) {
        FileObjectEntity root = objectMapper.selectByIdForUpdate(fileId);
        if (root == null) {
            return; // 幂等：文件不存在视为已删除
        }
        if (bindingMapper.findByOwner(fileId, ownerService, ownerType, ownerId) == null) {
            throw new FileAccessDeniedException(
                    "调用方对文件无绑定记录，禁止删除: fileId=" + fileId);
        }
        if (bindingMapper.countActiveByFileId(fileId) > 0) {
            throw new FileBoundException("文件存在活跃绑定，禁止删除: fileId=" + fileId);
        }
        storageGateway.deleteObject(root.getBucket(), root.getObjectKey());
        root.setStatus(STATUS_DELETED);
        root.setDeletedAt(clock.instant());
        // 乐观锁拦截器按实体当前 version 生成 WHERE version=旧、SET version=旧+1，
        // 并在 updateById 成功后把新版本写回实体（Field.set），此处不得再手动 +1。
        int versionAfterDelete = root.getVersion() + 1;
        int updated = objectMapper.updateById(root);
        if (updated != 1) {
            throw new VersionConflictException(
                    "文件对象版本冲突，删除失败: fileId=" + fileId);
        }
        // 删除成功后发布 FileDeleted（aggregateVersion=删除后根版本）。
        eventPublisher.fileDeleted(
                fileId, root.getObjectKey(), ownerService, ownerType, ownerId, reason,
                versionAfterDelete);
    }
}
