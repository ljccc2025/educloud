package com.educloud.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.Instant;

/** 平台公开配置（platform_public_config）。只允许非敏感配置（安全设计第 9 节）。 */
@Data
@TableName("platform_public_config")
public class PlatformPublicConfigEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String configKey;

    private String configValue;

    private String valueType;

    private String description;

    @Version
    private Integer version;

    private Long createdBy;

    private Instant createdAt;

    private Long updatedBy;

    private Instant updatedAt;
}
