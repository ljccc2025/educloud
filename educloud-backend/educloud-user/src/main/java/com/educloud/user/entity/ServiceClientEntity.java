package com.educloud.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.Instant;

/**
 * 服务客户端（service_client）。allowed_audiences_json/allowed_scopes_json 为 JSON 数组字符串；
 * token_version 递增可立即撤销已签发的全部服务 Token（安全设计第 8 节）。
 */
@Data
@TableName("service_client")
public class ServiceClientEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String clientId;

    private String status;

    private String allowedAudiencesJson;

    private String allowedScopesJson;

    private Long tokenVersion;

    private Long createdBy;

    private Instant createdAt;

    private Long updatedBy;

    private Instant updatedAt;

    @Version
    private Integer version;
}
