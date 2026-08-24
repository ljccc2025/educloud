package com.educloud.order.service.impl;

import com.educloud.order.exception.OrderBizException;
import com.educloud.order.exception.OrderErrorCode;
import com.educloud.order.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {

    private static final String KEY_PREFIX = "order:idempotency:";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(10);

    private static final String LUA_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then\n" +
            "    return redis.call('del', KEYS[1])\n" +
            "else\n" +
            "    return 0\n" +
            "end";

    private static final RedisScript<Long> SCRIPT = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public String generateToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String key = KEY_PREFIX + userId + ":" + token;
        stringRedisTemplate.opsForValue().set(key, token, TOKEN_TTL);
        return token;
    }

    @Override
    public void validateAndConsume(Long userId, String token) {
        if (token == null || token.isBlank()) {
            throw new OrderBizException(OrderErrorCode.DUPLICATE_ORDER_SUBMISSION);
        }
        String key = KEY_PREFIX + userId + ":" + token;
        Long result = stringRedisTemplate.execute(SCRIPT, List.of(key), token);
        if (result == null || result == 0L) {
            throw new OrderBizException(OrderErrorCode.DUPLICATE_ORDER_SUBMISSION);
        }
    }
}
