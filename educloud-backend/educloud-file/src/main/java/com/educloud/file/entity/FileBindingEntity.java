package com.educloud.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 业务绑定（file_binding）：文件对象与业务（服务/类型/业务 ID）的关联记录。
 *
 * <p>唯一键 (file_id, owner_service, owner_type, owner_id)；解绑用 unbound_at 软标记，
 * 历史绑定保留可审计。字段与 V001__file.sql 一一对应。</p>
 */
@Data
@TableName("file_binding")
public class FileBindingEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 绑定的文件对象 ID（file_object 聚合根）。 */
    private Long fileId;

    /** 属主服务名，如 educloud-user。 */
    private String ownerService;

    /** 属主类型，如 USER_AVATAR / COURSE_COVER。 */
    private String ownerType;

    /** 属主业务 ID（字符串化，兼容非数值主键）。 */
    private String ownerId;

    private Instant boundAt;

    /** 解绑时间；NULL 表示当前仍处于绑定状态。 */
    private Instant unboundAt;
}
