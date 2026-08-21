package com.educloud.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * Refresh 会话（refresh_session）。只保存 Token 哈希；family_id 对应 Access Token 的 sid；
 * status 属于 ACTIVE/ROTATED/REVOKED/EXPIRED（安全设计第 3.2 节、数据设计第 3 节）。
 */
@Data
@TableName("refresh_session")
public class RefreshSessionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String familyId;

    private String tokenId;

    private String parentTokenId;

    private String replacedByTokenId;

    private Long userId;

    private String sessionTokenHash;

    private String status;

    private String clientType;

    private String clientFingerprintHash;

    private Instant issuedAt;

    private Instant consumedAt;

    private Instant expiresAt;

    private Instant revokedAt;

    private String revokeReason;
}
