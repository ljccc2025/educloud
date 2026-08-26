package com.educloud.analytics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.educloud.analytics.enums.RebuildStage;
import com.educloud.analytics.enums.RebuildStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("analytics_rebuild_task")
public class AnalyticsRebuildTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_no")
    private String taskNo;

    @TableField("trigger_by")
    private String triggerBy;

    @TableField("status")
    private RebuildStatus status;

    @TableField("stage")
    private RebuildStage stage;

    @TableField("total_items")
    private Integer totalItems;

    @TableField("processed_items")
    private Integer processedItems;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
