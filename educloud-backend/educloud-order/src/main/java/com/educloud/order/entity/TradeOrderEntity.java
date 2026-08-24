package com.educloud.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("trade_order")
public class TradeOrderEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String orderNo;

    private Long studentId;

    /**
     * PENDING_PAYMENT, PAID, CANCELLED, REFUNDED
     */
    private String status;

    private BigDecimal originalAmount;

    private BigDecimal payableAmount;

    private String currency;

    private LocalDateTime expiresAt;

    private LocalDateTime paidAt;

    private LocalDateTime cancelledAt;

    private String idempotencyKeyHash;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
