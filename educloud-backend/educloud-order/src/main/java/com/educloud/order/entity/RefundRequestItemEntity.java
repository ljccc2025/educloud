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
@TableName("refund_request_item")
public class RefundRequestItemEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long refundRequestId;

    private Long orderItemId;

    private Long courseId;

    private BigDecimal requestedAmount;

    private BigDecimal approvedAmount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
