package com.educloud.ai.chat;

import com.educloud.ai.config.AiProperties;
import com.educloud.ai.exception.AiErrorCode;
import com.educloud.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    private static final long STUDENT_ID = 2001L;

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private QuotaService quotaService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        quotaService = new QuotaService(redisTemplate, new AiProperties(
                new AiProperties.ProviderProperties("openai-compatible", "https://x/v1", "m", "k", false, 1024),
                new AiProperties.TimeoutProperties(5000, 25000),
                new AiProperties.QuotaProperties(50, 2000000),
                new AiProperties.ContextProperties(10, 3000),
                new AiProperties.JwtProperties("", "i", "a")));
    }

    @Test
    void allowsWhenBelowPersonalQuota() {
        when(valueOperations.get(personalKey())).thenReturn("49");

        assertThatCode(() -> quotaService.ensureWithinLimits(STUDENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void rejectsWhenPersonalQuotaReached() {
        when(valueOperations.get(personalKey())).thenReturn("50");

        assertThatThrownBy(() -> quotaService.ensureWithinLimits(STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_QUOTA_EXCEEDED);
    }

    @Test
    void rejectsWhenGlobalTokenBudgetReached() {
        when(valueOperations.get(personalKey())).thenReturn("3");
        when(valueOperations.get("educloud:ai:quota:daily-tokens")).thenReturn("2000000");

        assertThatThrownBy(() -> quotaService.ensureWithinLimits(STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_GLOBAL_BUDGET_EXCEEDED);
    }

    @Test
    void missingCountersTreatAsZero() {
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThatCode(() -> quotaService.ensureWithinLimits(STUDENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void recordUsageIncrementsPersonalCounterAndSetsTtlOnFirstIncrement() {
        when(valueOperations.increment(personalKey())).thenReturn(1L);
        when(valueOperations.increment("educloud:ai:quota:daily-tokens", 370L)).thenReturn(370L);

        quotaService.recordUsage(STUDENT_ID, 370L);

        // 递增键必须是 8 位 yyyyMMdd 日期（规格 §3），防止 LocalDate.toString() 的 ISO 破折号格式回流
        verify(valueOperations).increment(argThat(QuotaServiceTest::incrementKeyMustBeEightDigitDate));
        // 日期键由应用侧 JVM 本地时间生成（MySQL/Redis 与 JVM 时区不同，不允许在 SQL/脚本里算日期）
        verify(redisTemplate).expire(eq(personalKey()), any(Duration.class));
        verify(valueOperations).increment("educloud:ai:quota:daily-tokens", 370L);
        verify(redisTemplate).expire(eq("educloud:ai:quota:daily-tokens"), any(Duration.class));
    }

    @Test
    void recordUsageDoesNotResetTtlOnSubsequentIncrements() {
        when(valueOperations.increment(personalKey())).thenReturn(2L);
        when(valueOperations.increment("educloud:ai:quota:daily-tokens", 100L)).thenReturn(470L);

        quotaService.recordUsage(STUDENT_ID, 100L);

        verify(redisTemplate, never()).expire(startsWith("educloud:ai:quota:2"), any(Duration.class));
    }

    @Test
    void recordUsageRepairsMissingTtlOnGlobalKeyAfterCrash() {
        // INCR 后 EXPIRE 前崩溃的孤儿全局键：递增结果!=增量（非首增）且 getExpire=-1 → 补设 TTL
        when(valueOperations.increment(personalKey())).thenReturn(1L);
        when(valueOperations.increment("educloud:ai:quota:daily-tokens", 370L)).thenReturn(9999L);
        when(redisTemplate.getExpire("educloud:ai:quota:daily-tokens")).thenReturn(-1L);

        quotaService.recordUsage(STUDENT_ID, 370L);

        verify(redisTemplate).expire(eq("educloud:ai:quota:daily-tokens"), any(Duration.class));
    }

    private static String personalKey() {
        // 与实现共用 yyyyMMdd 契约（规格 §3）；另由 captureIncrementKeyAssertsEightDigitDate 锁死格式
        return "educloud:ai:quota:" + STUDENT_ID + ":"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private static boolean incrementKeyMustBeEightDigitDate(String key) {
        // ArgumentMatcher 需要返回 boolean；格式不符时由 verify 失败呈现
        return key.matches("educloud:ai:quota:2001:\\d{8}");
    }
}
