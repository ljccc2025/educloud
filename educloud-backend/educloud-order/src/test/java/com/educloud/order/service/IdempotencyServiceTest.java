package com.educloud.order.service;

import com.educloud.order.exception.OrderBizException;
import com.educloud.order.exception.OrderErrorCode;
import com.educloud.order.service.impl.IdempotencyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest {

    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private IdempotencyServiceImpl idempotencyService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        idempotencyService = new IdempotencyServiceImpl(stringRedisTemplate);
    }

    @Test
    void generatesTokenAndStoresInRedisWithTtl() {
        Long userId = 2001L;
        String token = idempotencyService.generateToken(userId);

        assertThat(token).isNotBlank();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(valueOperations).set(keyCaptor.capture(), valCaptor.capture(), ttlCaptor.capture());
        assertThat(keyCaptor.getValue()).isEqualTo("order:idempotency:2001:" + token);
        assertThat(valCaptor.getValue()).isEqualTo(token);
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @SuppressWarnings("unchecked")
    void validatesAndConsumesTokenSuccessfully() {
        Long userId = 2001L;
        String token = "valid-token-123";
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(1L);

        idempotencyService.validateAndConsume(userId, token);

        verify(stringRedisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("order:idempotency:2001:" + token)),
                eq(token));
    }

    @Test
    @SuppressWarnings("unchecked")
    void throwsExceptionWhenTokenIsInvalidOrAlreadyConsumed() {
        Long userId = 2001L;
        String token = "used-token-123";
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(0L);

        assertThatThrownBy(() -> idempotencyService.validateAndConsume(userId, token))
                .isInstanceOf(OrderBizException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.DUPLICATE_ORDER_SUBMISSION);
    }

    @Test
    void throwsExceptionWhenTokenIsBlankOrNull() {
        assertThatThrownBy(() -> idempotencyService.validateAndConsume(2001L, null))
                .isInstanceOf(OrderBizException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.DUPLICATE_ORDER_SUBMISSION);

        assertThatThrownBy(() -> idempotencyService.validateAndConsume(2001L, ""))
                .isInstanceOf(OrderBizException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.DUPLICATE_ORDER_SUBMISSION);
    }
}
