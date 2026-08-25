package com.educloud.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.order.entity.TradeOrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface TradeOrderMapper extends BaseMapper<TradeOrderEntity> {

    @Update("UPDATE trade_order SET status = #{toStatus}, version = version + 1, updated_at = NOW(3) "
            + "WHERE id = #{id} AND status = #{fromStatus}")
    int updateStatusWithCas(
            @Param("id") Long id,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus);

    // BUG-016 修复：置 PAID 的 CAS 追加 expires_at > NOW(3) 双保险，防止延时关单
    // 消息丢失/延迟时已过期订单被支付（与 mockPay 的前置校验互为冗余）。
    @Update("UPDATE trade_order SET status = #{toStatus}, paid_at = #{paidAt}, version = version + 1, updated_at = NOW(3) "
            + "WHERE id = #{id} AND status = #{fromStatus} AND expires_at > NOW(3)")
    int updateStatusToPaidWithCas(
            @Param("id") Long id,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("paidAt") LocalDateTime paidAt);

    @Update("UPDATE trade_order SET status = #{toStatus}, cancelled_at = #{cancelledAt}, version = version + 1, updated_at = NOW(3) "
            + "WHERE id = #{id} AND status = #{fromStatus}")
    int updateStatusToCancelledWithCas(
            @Param("id") Long id,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("cancelledAt") LocalDateTime cancelledAt);

    /**
     * 兑底关单（BUG-018 修复）：延时关单消息丢失/MQ 积压时由定时扫描兑底，
     * 批量 CAS 关闭已过期的待支付订单（status=CANCELLED，与 OrderStatus 枚举对齐）。
     */
    @Update("UPDATE trade_order SET status = 'CANCELLED', cancelled_at = NOW(3), version = version + 1, updated_at = NOW(3) "
            + "WHERE status = 'PENDING_PAYMENT' AND expires_at < NOW(3) LIMIT #{limit}")
    int cancelExpiredPendingOrders(@Param("limit") int limit);

    @Select("SELECT * FROM trade_order WHERE id = #{id} FOR UPDATE")
    TradeOrderEntity selectByIdForUpdate(@Param("id") Long id);
}
