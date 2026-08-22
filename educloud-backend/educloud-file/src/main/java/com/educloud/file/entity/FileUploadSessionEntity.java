package com.educloud.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 上传会话（file_upload_session）：PUT URL 预签发放/轮换/过期，以及对象正式登记前的中间状态。
 *
 * <p>字段与 V001__file.sql 一一对应（map-underscore-to-camel-case 自动映射）；
 * 注意 version 仅作为会话轮换次数的普通计数，不参与乐观锁（乐观锁仅 file_object.version）。</p>
 */
@Data
@TableName("file_upload_session")
public class FileUploadSessionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 上传者用户 ID（user 服务账号聚合根 ID）。 */
    private Long uploaderId;

    /** 对象存储键（唯一键 uk_upload_session_object_key）。 */
    private String objectKey;

    /** 对象存储桶名。 */
    private String bucket;

    /** 客户端原始文件名（仅展示，不参与存储寻址）。 */
    private String originalName;

    /** 客户端申报的 Content-Type。 */
    private String contentType;

    /** 期望大小（字节），可选，用于上传前校验。 */
    private Long expectedSizeBytes;

    /** PENDING/COMPLETED/EXPIRED/ABORTED。 */
    private String status;

    /** PUT URL 到期时间。 */
    private Instant putUrlExpiresAt;

    /** 会话整体到期时间。 */
    private Instant expiresAt;

    private Instant createdAt;

    /** 会话轮换计数（普通字段，非乐观锁版本）。 */
    private Integer version;
}
