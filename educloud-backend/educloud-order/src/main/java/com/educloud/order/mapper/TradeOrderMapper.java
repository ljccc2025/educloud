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

    @Update("UPDATE trade_order SET status = #{toStatus}, paid_at = #{paidAt}, version = version + 1, updated_at = NOW(3) "
            + "WHERE id = #{id} AND status = #{fromStatus}")
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

    @Select("SELECT * FROM trade_order WHERE id = #{id} FOR UPDATE")
    TradeOrderEntity selectByIdForUpdate(@Param("id") Long id);
}
