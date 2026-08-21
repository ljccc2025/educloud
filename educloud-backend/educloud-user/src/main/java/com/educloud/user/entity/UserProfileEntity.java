package com.educloud.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.Instant;

/**
 * 用户档案（user_profile）。avatar_file_id 仅记录 File 对象 ID，不保存 URL（数据设计第 3 节）。
 */
@Data
@TableName("user_profile")
public class UserProfileEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String displayName;

    private Long avatarFileId;

    private String bio;

    private String locale;

    private Long createdBy;

    private Instant createdAt;

    private Long updatedBy;

    private Instant updatedAt;

    @Version
    private Integer version;
}
