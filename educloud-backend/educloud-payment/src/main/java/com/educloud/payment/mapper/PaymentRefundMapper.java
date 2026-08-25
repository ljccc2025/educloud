package com.educloud.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.payment.entity.PaymentRefundEntity;
import com.educloud.payment.enums.RefundStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface PaymentRefundMapper extends BaseMapper<PaymentRefundEntity> {

    @Update("""
        UPDATE payment_refund
        SET status = #{targetStatus},
            refunded_at = #{refundedAt},
            channel_refund_no = #{channelRefundNo},
            version = version + 1
        WHERE id = #{id}
          AND status = #{currentStatus}
    """)
    int updateStatusCas(
            @Param("id") Long id,
            @Param("currentStatus") RefundStatus currentStatus,
            @Param("targetStatus") RefundStatus targetStatus,
            @Param("refundedAt") LocalDateTime refundedAt,
            @Param("channelRefundNo") String channelRefundNo);
}
