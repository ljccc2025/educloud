package com.educloud.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.educloud.file.entity.FileBindingEntity;
import com.educloud.file.entity.FileObjectEntity;
import com.educloud.file.exception.FileNotAvailableException;
import com.educloud.file.exception.FileNotFoundException;
import com.educloud.file.exception.VersionConflictException;
import com.educloud.file.mapper.FileBindingMapper;
import com.educloud.file.mapper.FileObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

/**
 * 业务绑定服务：bind/unbind 均在事务内先 SELECT ... FOR UPDATE 锁 file_object 根，
 * 写 file_binding 并递增根 version（乐观锁拦截器生成 WHERE version=旧）。
 *
 * <p>依据：M04 设计规格第 7.2 节与第 5 节 —— 绑定幂等（同属主重复 bind 不重复插入）；
 * 唯一键 uk_file_binding 以 try/catch DuplicateKeyException 兜并发；
 * unbind 用 unbound_at 软标记保留审计历史，未绑定幂等返回。
 * 事件（FileBound/FileUnbound）由任务 11 经 Outbox 发布，本任务不发布。</p>
 */
@Service
public class FileBindingService {

    private final FileObjectMapper objectMapper;
    private final FileBindingMapper bindingMapper;
    private final Clock clock;

    public FileBindingService(
            FileObjectMapper objectMapper,
            FileBindingMapper bindingMapper,
            Clock clock) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.bindingMapper = Objects.requireNonNull(bindingMapper, "bindingMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 绑定文件到业务属主：锁根 → 校验存在/AVAILABLE → 查活跃绑定（存在则幂等返回）→
     * 插入 binding（DuplicateKeyException 兜并发）→ 根 version+1。
     */
    @Transactional
    public void bind(Long fileId, String ownerService, String ownerType, String ownerId) {
        FileObjectEntity root = objectMapper.selectByIdForUpdate(fileId);
        if (root == null) {
            throw new FileNotFoundException("文件对象不存在: fileId=" + fileId);
        }
        if (!FileObjectService.STATUS_AVAILABLE.equals(root.getStatus())) {
            throw new FileNotAvailableException(
                    "文件对象不可绑定: fileId=" + fileId + ", status=" + root.getStatus());
        }

        FileBindingEntity active = bindingMapper.findActiveByOwner(
                fileId, ownerService, ownerType, ownerId);
        if (active != null) {
            return; // 幂等：同属主已绑定
        }

        FileBindingEntity binding = new FileBindingEntity();
        binding.setId(IdWorker.getId());
        binding.setFileId(fileId);
        binding.setOwnerService(ownerService);
        binding.setOwnerType(ownerType);
        binding.setOwnerId(ownerId);
        binding.setBoundAt(clock.instant());
        try {
            bindingMapper.insert(binding);
        } catch (DuplicateKeyException e) {
            // 并发兜底：另一事务已插入同一 uk_file_binding → 再查活跃绑定幂等返回。
            FileBindingEntity concurrent = bindingMapper.findActiveByOwner(
                    fileId, ownerService, ownerType, ownerId);
            if (concurrent == null) {
                // 唯一键冲突但无活跃绑定：属主曾解绑，恢复历史行（unbound_at 置 NULL）。
                FileBindingEntity historical = bindingMapper.selectOne(
                        new LambdaQueryWrapper<FileBindingEntity>()
                                .eq(FileBindingEntity::getFileId, fileId)
                                .eq(FileBindingEntity::getOwnerService, ownerService)
                                .eq(FileBindingEntity::getOwnerType, ownerType)
                                .eq(FileBindingEntity::getOwnerId, ownerId));
                if (historical == null) {
                    throw e; // 非预期唯一键冲突，原样上抛
                }
                historical.setUnboundAt(null);
                historical.setBoundAt(clock.instant());
                bindingMapper.updateById(historical);
            } else {
                return;
            }
        }

        // 乐观锁拦截器按实体当前 version 生成 WHERE version=旧、SET version=旧+1，
        // 并在 updateById 成功后把新版本写回实体（Field.set），此处不得再手动 +1。
        int updated = objectMapper.updateById(root);
        if (updated != 1) {
            throw new VersionConflictException(
                    "文件对象版本冲突，绑定失败: fileId=" + fileId);
        }
    }

    /**
     * 解绑文件与业务属主：锁根 → 查活跃绑定（无则幂等返回）→ unbound_at 置当前时间 →
     * 根 version+1。文件不存在同样幂等返回。
     */
    @Transactional
    public void unbind(Long fileId, String ownerService, String ownerType, String ownerId) {
        FileObjectEntity root = objectMapper.selectByIdForUpdate(fileId);
        if (root == null) {
            return; // 幂等：文件不存在视为无绑定可解
        }
        FileBindingEntity active = bindingMapper.findActiveByOwner(
                fileId, ownerService, ownerType, ownerId);
        if (active == null) {
            return; // 幂等：未绑定
        }
        active.setUnboundAt(clock.instant());
        bindingMapper.updateById(active);

        // 同 bind：实体 version 保持旧值交给拦截器递增，成功后拦截器写回新版本，再检查命中行数。
        int updated = objectMapper.updateById(root);
        if (updated != 1) {
            throw new VersionConflictException(
                    "文件对象版本冲突，解绑失败: fileId=" + fileId);
        }
    }
}
