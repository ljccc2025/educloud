package com.educloud.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 服务客户端凭据（service_client_credential）。secret 只存 SHA-256 哈希；
 * 同一 client 最多一个 ACTIVE 与一个 GRACE（安全设计第 8 节）。
 */
@Data
@TableName("service_client_credential")
public class ServiceClientCredentialEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long serviceClientId;

    private Integer credentialVersion;

    private String secretHash;

    /** ACTIVE/GRACE/REVOKED。 */
    private String status;

    private Instant notBefore;

    private Instant expiresAt;

    private Instant revokedAt;
}
