package com.educloud.ai.chat;

import com.educloud.ai.config.AiProperties;
import com.educloud.ai.exception.AiErrorCode;
import com.educloud.common.error.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 配额与全局熔断（规格 §5.4）：个人 50 次/日 + 全局 200 万 token/日。
 * 计数在调用成功后累加（ChatService 负责）；失败调用不计次但照常落 ai_message 留痕。
 * Redis 不可用时检查直接抛异常（fail-closed，绝不放行无配额调用）。
 * 日期键与 TTL 一律用 JVM 本地时间在应用侧计算（VM 的 MySQL 会话是 UTC，禁止在 SQL 里算"今天"）。
 */
@Slf4j
@Component
public class QuotaService {

    private static final String PERSONAL_KEY_PREFIX = "educloud:ai:quota:";
    private static final String GLOBAL_TOKENS_KEY = "educloud:ai:quota:daily-tokens";

    private final StringRedisTemplate redisTemplate;
    private final AiProperties properties;

    public QuotaService(StringRedisTemplate redisTemplate, AiProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void ensureWithinLimits(Long studentId) {
        long used = readCounter(personalKey(studentId));
        if (used >= properties.quota().dailyRequests()) {
            throw new BusinessException(AiErrorCode.AI_QUOTA_EXCEEDED,
                    "Student " + studentId + " used " + used + " AI requests today");
        }
        long globalTokens = readCounter(GLOBAL_TOKENS_KEY);
        if (globalTokens >= properties.quota().dailyTokens()) {
            log.warn("AI global daily token budget reached: {}", globalTokens);
            throw new BusinessException(AiErrorCode.AI_GLOBAL_BUDGET_EXCEEDED,
                    "Global daily AI token budget exceeded: " + globalTokens);
        }
    }

    public void recordUsage(Long studentId, long totalTokens) {
        String personal = personalKey(studentId);
        Long personalCount = redisTemplate.opsForValue().increment(personal);
        if (personalCount != null && personalCount == 1L) {
            redisTemplate.expire(personal, untilMidnight());
        }
        Long globalCount = redisTemplate.opsForValue().increment(GLOBAL_TOKENS_KEY, totalTokens);
        if (globalCount != null && globalCount == totalTokens) {
            redisTemplate.expire(GLOBAL_TOKENS_KEY, untilMidnight());
        }
    }

    private long readCounter(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0L : Long.parseLong(value);
    }

    private static String personalKey(Long studentId) {
        return PERSONAL_KEY_PREFIX + studentId + ":" + LocalDate.now();
    }

    private static Duration untilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMidnight = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.MIDNIGHT);
        return Duration.between(now, nextMidnight);
    }
}
