package com.educloud.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.payment.entity.PaymentOrderEntity;
import com.educloud.payment.enums.PaymentStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrderEntity> {

    @Update("""
        UPDATE payment_order
        SET status = #{targetStatus},
            paid_at = #{paidAt},
            channel_trade_no = #{channelTradeNo},
            version = version + 1
        WHERE id = #{id}
          AND status IN ('INITIATED', 'PAYING')
          AND expires_at > #{now}
          AND deleted = 0
    """)
    int updateStatusToSuccessCas(
            @Param("id") Long id,
            @Param("targetStatus") PaymentStatus targetStatus,
            @Param("paidAt") LocalDateTime paidAt,
            @Param("channelTradeNo") String channelTradeNo,
            @Param("now") LocalDateTime now);

    @Update("""
        UPDATE payment_order
        SET status = #{targetStatus},
            version = version + 1
        WHERE id = #{id}
          AND status IN ('INITIATED', 'PAYING')
          AND deleted = 0
    """)
    int updateStatusToTerminalCas(
            @Param("id") Long id,
            @Param("targetStatus") PaymentStatus targetStatus);
}
