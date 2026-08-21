package com.educloud.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.Instant;

/**
 * 角色（sys_role）。built_in 角色不可删除（安全设计第 5 节）。
 */
@Data
@TableName("sys_role")
public class SysRoleEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String code;

    private String name;

    private String description;

    private String status;

    private Boolean builtIn;

    private Long createdBy;

    private Instant createdAt;

    private Long updatedBy;

    private Instant updatedAt;

    @Version
    private Integer version;
}
