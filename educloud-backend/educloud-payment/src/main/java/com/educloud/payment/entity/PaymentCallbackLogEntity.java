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
@TableName("payment_callback_log")
public class PaymentCallbackLogEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("channel_code")
    private PaymentChannel channelCode;

    @TableField("notify_id")
    private String notifyId;

    @TableField("request_hash")
    private String requestHash;

    @TableField("raw_payload")
    private String rawPayload;

    @TableField("verify_result")
    private String verifyResult;

    @TableField("processed_status")
    private String processedStatus;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
