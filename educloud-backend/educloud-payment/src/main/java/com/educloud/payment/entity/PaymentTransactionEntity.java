package com.educloud.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.educloud.payment.enums.PaymentChannel;
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
@TableName("payment_transaction")
public class PaymentTransactionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("payment_order_id")
    private Long paymentOrderId;

    @TableField("transaction_no")
    private String transactionNo;

    @TableField("channel_code")
    private PaymentChannel channelCode;

    @TableField("action_type")
    private String actionType;

    @TableField("amount_cents")
    private Long amountCents;

    @TableField("fee_cents")
    private Long feeCents;

    @TableField("raw_request")
    private String rawRequest;

    @TableField("raw_response")
    private String rawResponse;

    @TableField("status")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
