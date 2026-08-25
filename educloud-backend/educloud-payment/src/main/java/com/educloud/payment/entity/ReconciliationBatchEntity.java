package com.educloud.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.ReconciliationBatchStatus;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("reconciliation_batch")
public class ReconciliationBatchEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("batch_no")
    private String batchNo;

    @TableField("reconcile_date")
    private LocalDate reconcileDate;

    @TableField("channel_code")
    private PaymentChannel channelCode;

    @TableField("total_count")
    private Integer totalCount;

    @TableField("total_amount_cents")
    private Long totalAmountCents;

    @TableField("diff_count")
    private Integer diffCount;

    @TableField("status")
    private ReconciliationBatchStatus status;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;
}
