package com.educloud.order.messaging;

import com.educloud.order.mapper.TradeOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时订单兑底关单扫描（BUG-018 修复）：延时关单消息（TTL+DLX）发送失败或
 * MQ 积压延迟到达时，由本任务每分钟批量 CAS 关闭已过期的待支付订单——
 * 与 BUG-016 的支付侧 expires_at 校验互为双保险，确保交易窗口语义。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredOrderSweeper {

    private static final int BATCH_SIZE = 100;

    private final TradeOrderMapper tradeOrderMapper;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void cancelExpiredPendingOrders() {
        int cancelled = tradeOrderMapper.cancelExpiredPendingOrders(BATCH_SIZE);
        if (cancelled > 0) {
            log.warn("Expired order sweeper cancelled {} PENDING_PAYMENT orders past expires_at "
                    + "(delay message lost or delayed)", cancelled);
        }
    }
}
