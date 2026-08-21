package com.educloud.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** HTTP 幂等记录（idempotency_record）。匿名注册 user_id 用 0 约定（数据设计第 14 节）。 */
@Data
@TableName("idempotency_record")
public class IdempotencyRecordEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String operation;

    private String idempotencyKeyHash;

    private String requestHash;

    private Integer responseStatus;

    private String responseBodyJson;

    private Instant expiresAt;

    private Instant createdAt;
}
