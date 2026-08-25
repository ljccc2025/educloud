package com.educloud.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.payment.entity.PaymentOutboxEventEntity;
import com.educloud.payment.enums.OutboxStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PaymentOutboxEventMapper extends BaseMapper<PaymentOutboxEventEntity> {

    @Select("""
        SELECT * FROM payment_outbox_event
        WHERE status = 'PENDING'
          AND next_retry_time <= #{now}
        ORDER BY id ASC
        LIMIT #{limit}
    """)
    List<PaymentOutboxEventEntity> findPendingEvents(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    @Update("""
        UPDATE payment_outbox_event
        SET status = #{targetStatus}
        WHERE id = #{id}
          AND status = #{currentStatus}
    """)
    int updateStatusCas(
            @Param("id") Long id,
            @Param("currentStatus") OutboxStatus currentStatus,
            @Param("targetStatus") OutboxStatus targetStatus);

    @Update("""
        UPDATE payment_outbox_event
        SET status = 'PUBLISHED',
            published_at = #{now}
        WHERE id = #{id}
    """)
    int markPublished(
            @Param("id") Long id,
            @Param("now") LocalDateTime now);

    @Update("""
        UPDATE payment_outbox_event
        SET status = #{status},
            retry_count = retry_count + 1,
            next_retry_time = #{nextRetryTime}
        WHERE id = #{id}
    """)
    int markFailed(
            @Param("id") Long id,
            @Param("status") OutboxStatus status,
            @Param("nextRetryTime") LocalDateTime nextRetryTime);
}
