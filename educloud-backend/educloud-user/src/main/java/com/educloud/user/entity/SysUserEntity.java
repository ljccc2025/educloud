package com.educloud.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.Instant;

/**
 * 账号聚合根（sys_user）。
 * 依据：M03 设计规格第 3.3 节与数据设计第 3 节；token_version 用于在线撤销，
 * version 为乐观锁并作为 UserStatusChanged 连续事件版本。
 */
@Data
@TableName("sys_user")
public class SysUserEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    private String email;

    private String phone;

    /** BCrypt 哈希；只写不返回（安全设计第 4 节）。 */
    private String passwordHash;

    /** STUDENT/TEACHER/ADMIN（Gateway 校验器硬编码集合）。 */
    private String userType;

    /** ACTIVE/LOCKED/DISABLED。 */
    private String status;

    private Long tokenVersion;

    private Boolean emailVerified;

    private Integer failedLoginCount;

    private Instant lockedUntil;

    private Instant lastLoginAt;

    private Long createdBy;

    private Instant createdAt;

    private Long updatedBy;

    private Instant updatedAt;

    @Version
    private Integer version;
}
