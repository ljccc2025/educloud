package com.educloud.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("trade_order_item")
public class TradeOrderItemEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long orderId;

    private Long courseId;

    private String courseTitleSnapshot;

    private Long coverFileIdSnapshot;

    private BigDecimal unitPrice;

    private Integer quantity;

    private BigDecimal lineAmount;

    private BigDecimal refundReservedAmount;

    private BigDecimal refundedAmount;

    /**
     * UNFULFILLED, FULFILLED, REVOKED
     */
    private String fulfillmentStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
