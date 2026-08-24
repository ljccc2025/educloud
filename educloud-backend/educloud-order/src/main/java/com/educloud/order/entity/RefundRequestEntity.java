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
@TableName("refund_request")
public class RefundRequestEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String refundNo;

    private Long orderId;

    private Long studentId;

    private BigDecimal requestedAmount;

    private String reason;

    /**
     * PENDING_REVIEW, APPROVED, REJECTED, SUCCESS
     */
    private String status;

    private Long reviewedBy;

    private String reviewReason;

    private LocalDateTime reviewedAt;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
