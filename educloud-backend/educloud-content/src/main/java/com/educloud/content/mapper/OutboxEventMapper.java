package com.educloud.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.content.entity.OutboxEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Outbox 事件表 Mapper（角色化动态流阶段 2）：参照 course 模块的「批量 CAS 认领 +
 * 归属实例取回」模式，多实例部署下同一事件仅被一个实例认领并投递。
 *
 * <p>状态机：PENDING --claimPending--> CLAIMED --markPublished--> PUBLISHED；
 * CLAIMED --markFailedAttempt--> PENDING（attempt_count+1、退避排程）；
 * CLAIMED --markFailed--> FAILED（达重试阈值终态，人工介入）；
 * CLAIMED 且 updated_at 超时 --releaseStaleClaims--> PENDING（实例崩溃恢复）。
 * 依赖 V003 迁移新增的 updated_at / claim_owner 列。</p>
 */
@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEventEntity> {

    /**
     * CAS 认领一批到期 PENDING 事件：单条 UPDATE 原子置 CLAIMED 并记录认领实例。
     * MySQL 不允许 UPDATE 直接子查询同表，故内层再包一层派生表。
     *
     * @param owner       本实例认领标识（仅归属实例可取回本批）
     * @param maxAttempts 超过该尝试次数的事件不再认领（终态放弃，人工介入）
     * @param batchSize   单批认领上限
     * @return 认领成功行数（0 = 无到期可认领事件）
     */
    @Update("""
        UPDATE outbox_event
        SET publish_status = 'CLAIMED', claim_owner = #{owner}
        WHERE id IN (
            SELECT id FROM (
                SELECT id FROM outbox_event
                WHERE publish_status = 'PENDING'
                  AND attempt_count < #{maxAttempts}
                  AND (next_attempt_at IS NULL OR next_attempt_at <= NOW())
                ORDER BY source_sequence
                LIMIT #{batchSize}
            ) t
        )
        """)
    int claimPending(@Param("owner") String owner,
                     @Param("maxAttempts") int maxAttempts,
                     @Param("batchSize") int batchSize);

    /** 取回本实例认领的 CLAIMED 事件（按 source_sequence 顺序投递，保持全局序）。 */
    @Select("""
        SELECT * FROM outbox_event
        WHERE publish_status = 'CLAIMED' AND claim_owner = #{owner}
        ORDER BY source_sequence
        """)
    List<OutboxEventEntity> selectClaimedByOwner(@Param("owner") String owner);

    /** 投递成功：CAS 置 PUBLISHED（仅认领者可终态，防并发双发）。 */
    @Update("UPDATE outbox_event SET publish_status = 'PUBLISHED', published_at = NOW(3), "
            + "claim_owner = NULL WHERE id = #{id} AND publish_status = 'CLAIMED'")
    int markPublished(@Param("id") Long id);

    /** 投递失败：回置 PENDING、attempt_count+1 并按退避时间推迟下次重试。
     *  next_attempt_at 由数据库时钟（NOW(3)）计算，避免应用本地时区与 DB 时区不一致
     *  导致认领条件 {@code next_attempt_at <= NOW()} 永远不成立（事件被误判为未来）。 */
    @Update("UPDATE outbox_event SET publish_status = 'PENDING', "
            + "attempt_count = attempt_count + 1, "
            + "next_attempt_at = TIMESTAMPADD(SECOND, #{delaySeconds}, NOW(3)), "
            + "claim_owner = NULL WHERE id = #{id} AND publish_status = 'CLAIMED'")
    int markFailedAttempt(@Param("id") Long id, @Param("delaySeconds") long delaySeconds);

    /** 投递失败且达重试阈值：CAS 置 FAILED（终态，人工介入）。
     *  next_attempt_at 同由数据库时钟计算，保持与认领条件同一时钟基准。 */
    @Update("UPDATE outbox_event SET publish_status = 'FAILED', "
            + "attempt_count = attempt_count + 1, "
            + "next_attempt_at = TIMESTAMPADD(SECOND, #{delaySeconds}, NOW(3)), "
            + "claim_owner = NULL WHERE id = #{id} AND publish_status = 'CLAIMED'")
    int markFailed(@Param("id") Long id, @Param("delaySeconds") long delaySeconds);

    /**
     * 回置超时未收敛的 CLAIMED 认领（实例崩溃恢复）：置回 PENDING 并 attempt_count+1，
     * 供其他实例重新认领；由 updated_at 判定（V003 迁移新增）。
     *
     * @return 回置行数
     */
    @Update("UPDATE outbox_event SET publish_status = 'PENDING', "
            + "attempt_count = attempt_count + 1, claim_owner = NULL "
            + "WHERE publish_status = 'CLAIMED' AND updated_at < NOW() - INTERVAL #{staleSeconds} SECOND")
    int releaseStaleClaims(@Param("staleSeconds") int staleSeconds);
}
