package com.educloud.search.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.educloud.search.enums.TaskStatus;
import com.educloud.search.enums.TaskType;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 搜索索引重建/修复任务实体类
 * 对应表：search_index_task
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("search_index_task")
public class IndexTaskEntity {

    /** 雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 任务唯一编号 */
    @TableField("task_no")
    private String taskNo;

    /** 目标物理索引名称 */
    @TableField("index_name")
    private String indexName;

    /** 关联别名 */
    @TableField("alias_name")
    private String aliasName;

    /** 任务类型: FULL_REBUILD / INCREMENTAL_REPAIR */
    @TableField("task_type")
    private TaskType taskType;

    /** 状态: PENDING / RUNNING / SUCCESS / FAILED */
    @TableField("status")
    private TaskStatus status;

    /** 待处理总记录数 */
    @TableField("total_records")
    private Integer totalRecords;

    /** 已成功处理记录数 */
    @TableField("processed_records")
    private Integer processedRecords;

    /** 失败记录数 */
    @TableField("failed_records")
    private Integer failedRecords;

    /** 失败异常原因 */
    @TableField("error_message")
    private String errorMessage;

    /** 开始时间 */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /** 完成时间 */
    @TableField("finished_at")
    private LocalDateTime finishedAt;

    /** 触发人 */
    @TableField("created_by")
    private String createdBy;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
