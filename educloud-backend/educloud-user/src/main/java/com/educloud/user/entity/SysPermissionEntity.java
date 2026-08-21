package com.educloud.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 权限目录（sys_permission）。权限码只增不改（安全设计第 6 节）。
 */
@Data
@TableName("sys_permission")
public class SysPermissionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String code;

    private String name;

    private String resource;

    private String action;

    private String description;
}
