package com.educloud.search.dto.response;

import com.educloud.search.entity.IndexTaskEntity;
import com.educloud.search.enums.TaskStatus;
import com.educloud.search.enums.TaskType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 索引重建/同步任务进度响应模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexTaskProgressResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务编号 */
    private String taskNo;

    /** 目标物理索引名称 */
    private String indexName;

    /** 关联别名 */
    private String aliasName;

    /** 任务类型: FULL_REBUILD / INCREMENTAL_REPAIR */
    private TaskType taskType;

    /** 任务状态: PENDING / RUNNING / SUCCESS / FAILED */
    private TaskStatus status;

    /** 待处理总记录数 */
    private Integer totalRecords;

    /** 已成功处理记录数 */
    private Integer processedRecords;

    /** 失败记录数 */
    private Integer failedRecords;

    /** 失败异常原因 */
    private String errorMessage;

    /** 进度百分比 (0 - 100) */
    private Integer progressPercent;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    /** 完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishedAt;

    /** 触发人 */
    private String createdBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 计算并获取进度百分比
     */
    public Integer getProgressPercent() {
        if (progressPercent != null) {
            return progressPercent;
        }
        if (status == TaskStatus.SUCCESS) {
            return 100;
        }
        if (totalRecords == null || totalRecords <= 0) {
            return 0;
        }
        if (processedRecords == null || processedRecords <= 0) {
            return 0;
        }
        int pct = (int) Math.min(100, Math.round((double) processedRecords * 100.0 / totalRecords));
        return pct;
    }

    /**
     * 从持久化实体转换为响应 DTO
     */
    public static IndexTaskProgressResponse fromEntity(IndexTaskEntity entity) {
        if (entity == null) {
            return null;
        }
        IndexTaskProgressResponse response = IndexTaskProgressResponse.builder()
                .taskNo(entity.getTaskNo())
                .indexName(entity.getIndexName())
                .aliasName(entity.getAliasName())
                .taskType(entity.getTaskType())
                .status(entity.getStatus())
                .totalRecords(entity.getTotalRecords())
                .processedRecords(entity.getProcessedRecords())
                .failedRecords(entity.getFailedRecords())
                .errorMessage(entity.getErrorMessage())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
        response.setProgressPercent(response.getProgressPercent());
        return response;
    }
}
