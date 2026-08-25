package com.educloud.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.educloud.payment.enums.PaymentChannel;
import com.educloud.payment.enums.PaymentStatus;
import com.educloud.payment.enums.TradeType;
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
@TableName("payment_order")
public class PaymentOrderEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("order_id")
    private Long orderId;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("user_id")
    private Long userId;

    @TableField("amount_cents")
    private Long amountCents;

    @TableField("currency")
    private String currency;

    @TableField("channel_code")
    private PaymentChannel channelCode;

    @TableField("trade_type")
    private TradeType tradeType;

    @TableField("status")
    private PaymentStatus status;

    @TableField("channel_trade_no")
    private String channelTradeNo;

    @TableField("pay_url")
    private String payUrl;

    @TableField("qr_code")
    private String qrCode;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("paid_at")
    private LocalDateTime paidAt;

    @Version
    @TableField("version")
    private Integer version;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
