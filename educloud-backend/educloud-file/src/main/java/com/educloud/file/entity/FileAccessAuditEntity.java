package com.educloud.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 访问审计（file_access_audit）：敏感/受保护文件访问的只追加事实记录。
 *
 * <p>action 取值：GRANT_SINGLE、GRANT_BATCH_DENIED、DELETE、DELETE_FORCE、STORAGE_TEST；
 * 数据库侧仅授予 INSERT/SELECT 权限。字段与 V001__file.sql 一一对应。</p>
 */
@Data
@TableName("file_access_audit")
public class FileAccessAuditEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 被访问的文件对象 ID。 */
    private Long fileId;

    /** 发起访问的用户 ID；匿名/系统动作可为 NULL。 */
    private Long userId;

    /** 动作类型（见类注释取值）。 */
    private String action;

    /** 结果：SUCCESS / DENIED 等。 */
    private String result;

    /** 客户端 IP，可为 NULL。 */
    private String ip;

    /** 关联请求 ID（UUID 36 字符，用于跨服务追踪）。 */
    private String requestId;

    private Instant occurredAt;
}
