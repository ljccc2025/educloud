package com.educloud.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.RefundStatus;
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
@TableName("payment_refund")
public class PaymentRefundEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("payment_order_id")
    private Long paymentOrderId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("order_id")
    private Long orderId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("refund_request_id")
    private Long refundRequestId;

    @TableField("refund_amount_cents")
    private Long refundAmountCents;

    @TableField("currency")
    private String currency;

    @TableField("reason")
    private String reason;

    @TableField("channel_code")
    private PaymentChannel channelCode;

    @TableField("channel_refund_no")
    private String channelRefundNo;

    @TableField("status")
    private RefundStatus status;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("audited_by")
    private Long auditedBy;

    @TableField("audited_at")
    private LocalDateTime auditedAt;

    @TableField("audit_remark")
    private String auditRemark;

    @TableField("refunded_at")
    private LocalDateTime refundedAt;

    @Version
    @TableField("version")
    private Integer version;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
