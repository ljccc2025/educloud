package com.educloud.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** 登录审计（login_audit）。登录名脱敏存储；只追加（安全设计第 14 节）。 */
@Data
@TableName("login_audit")
public class LoginAuditEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String loginNameMasked;

    private String result;

    private String failureCode;

    private String ip;

    private String userAgent;

    private String requestId;

    private Instant occurredAt;
}
