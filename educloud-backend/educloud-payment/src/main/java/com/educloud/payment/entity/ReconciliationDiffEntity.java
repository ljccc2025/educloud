package com.educloud.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.educloud.payment.enums.DiffType;
import com.educloud.payment.enums.ResolveAction;
import com.educloud.payment.enums.ResolveStatus;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("reconciliation_diff")
public class ReconciliationDiffEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("batch_id")
    private Long batchId;

    @TableField("diff_type")
    private DiffType diffType;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("payment_order_id")
    private Long paymentOrderId;

    @TableField("channel_trade_no")
    private String channelTradeNo;

    @TableField("local_amount_cents")
    private Long localAmountCents;

    @TableField("channel_amount_cents")
    private Long channelAmountCents;

    @TableField("local_status")
    private String localStatus;

    @TableField("channel_status")
    private String channelStatus;

    @TableField("resolve_status")
    private ResolveStatus resolveStatus;

    @TableField("resolve_action")
    private ResolveAction resolveAction;

    @TableField("resolve_remark")
    private String resolveRemark;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("resolved_by")
    private Long resolvedBy;

    @TableField("resolved_at")
    private LocalDateTime resolvedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
