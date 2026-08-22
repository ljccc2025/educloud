package com.educloud.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.Instant;

/**
 * 文件对象（file_object）：文件聚合根，映射对象存储中的单一对象（bucket + object_key 唯一）。
 *
 * <p>依据：2026-08-22-educloud-file-design.md 第 5 节 —— 绑定/解绑/删除在事务内先
 * SELECT ... FOR UPDATE 锁根行并递增 version；@Version 乐观锁用于绑定/删除时的
 * 版本递增并发保护，防止旧 FileBound 在 FileDeleted 后复活投影。</p>
 */
@Data
@TableName("file_object")
public class FileObjectEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 对象存储键（唯一键 uk_file_object_key）。 */
    private String objectKey;

    private String originalName;

    private String contentType;

    /** 文件大小（字节）。 */
    private Long sizeBytes;

    /** SHA-256 十六进制摘要（CHAR(64)，小写）。 */
    private String sha256;

    private String bucket;

    /** UPLOADING/AVAILABLE/QUARANTINED/DELETED。 */
    private String status;

    /** 上传者用户 ID。 */
    private Long uploaderId;

    private Instant uploadedAt;

    /** 删除时间；NULL 表示未删除（软删除标记由业务层维护）。 */
    private Instant deletedAt;

    /** 乐观锁版本：绑定/解绑/删除事务内递增，防止并发覆盖。 */
    @Version
    private Integer version;
}
