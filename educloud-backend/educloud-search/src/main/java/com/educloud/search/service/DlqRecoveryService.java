package com.educloud.search.service;

import org.springframework.amqp.core.Message;

import java.util.List;

/**
 * 索引同步死信恢复服务接口
 * 负责 DLQ 死信落库（index_sync_failure）与失败消息重放（定时 + 手动）。
 */
public interface DlqRecoveryService {

    /** 失败记录状态：待重放 */
    String STATUS_PENDING = "PENDING";
    /** 失败记录状态：重放成功 */
    String STATUS_RESOLVED = "RESOLVED";
    /** 失败记录状态：连续失败超过上限，不再自动重试 */
    String STATUS_DEAD = "DEAD";

    /**
     * 将 DLQ 死信消息落库为 PENDING 失败记录
     * 解析原始交换机/路由键、死信次数（x-death header）与失败原因。
     * 内部自捕获异常，绝不向上抛出（避免死信队列无限循环）。
     *
     * @param message RabbitMQ 死信消息
     */
    void recordFailure(Message message);

    /**
     * 重放所有 PENDING 失败记录（定时任务调用）
     * 成功标 RESOLVED；失败累加 retryCount，超过上限标 DEAD（仅告警日志）。
     *
     * @return 本轮退出 PENDING 状态的记录数
     */
    int replayPending();

    /**
     * 按记录 ID 手动重放单条失败记录（内部运维端点）
     *
     * @param id index_sync_failure 记录主键
     * @return 重放结果（含最新状态）
     */
    ReplayResult replayById(Long id);

    /**
     * 查询最近的失败记录（运维排查用，按发生时间倒序）
     *
     * @param limit 返回条数上限
     * @return 失败记录列表
     */
    List<ReplayResult> listRecentFailures(int limit);

    /**
     * 单条重放结果
     */
    record ReplayResult(Long id, String status, String message) {
    }
}
