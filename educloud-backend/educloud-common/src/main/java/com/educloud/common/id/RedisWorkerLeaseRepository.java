package com.educloud.common.id;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Redis/Lua implementation of the worker lease repository contract. */
public final class RedisWorkerLeaseRepository implements WorkerLeaseRepository {

    private static final int WORKER_COUNT = 32;
    private static final DefaultRedisScript<List> ACQUIRE_SCRIPT =
            script("acquire-worker.lua");
    private static final DefaultRedisScript<List> RENEW_SCRIPT =
            script("renew-worker.lua");
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            longScript("release-worker.lua");

    private final StringRedisTemplate redisTemplate;

    public RedisWorkerLeaseRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
    }

    @Override
    public Optional<WorkerLeaseGrant> tryAcquire(
            String environment,
            String ownerId,
            Duration leaseTtl) {
        validateEnvironment(environment);
        validateOwner(ownerId);
        long ttlMillis = validateTtl(leaseTtl);
        var keys = allWorkerKeys(environment);
        List<?> result = executeList(
                "acquire",
                ACQUIRE_SCRIPT,
                keys,
                ownerId,
                Long.toString(ttlMillis));
        requireSize(result, 2, "acquire");
        long worker = requireLong(result.get(0), "acquire worker id");
        long redisTime = requireLong(result.get(1), "acquire redis time");
        if (worker == -1) {
            return Optional.empty();
        }
        if (worker < 0 || worker >= WORKER_COUNT) {
            throw protocolFailure("acquire returned an invalid worker id");
        }
        return Optional.of(new WorkerLeaseGrant((int) worker, ownerId, redisTime));
    }

    @Override
    public Optional<WorkerLeaseGrant> renew(
            String environment,
            int workerId,
            String ownerId,
            Duration leaseTtl,
            long lastIssuedTimestamp) {
        validateEnvironment(environment);
        validateWorker(workerId);
        validateOwner(ownerId);
        long ttlMillis = validateTtl(leaseTtl);
        List<?> result = executeList(
                "renew",
                RENEW_SCRIPT,
                List.of(leaseKey(environment, workerId), watermarkKey(environment, workerId)),
                ownerId,
                Long.toString(ttlMillis),
                Long.toString(lastIssuedTimestamp));
        requireSize(result, 2, "renew");
        long renewed = requireLong(result.get(0), "renew status");
        long redisTime = requireLong(result.get(1), "renew redis time");
        if (renewed == 0) {
            return Optional.empty();
        }
        if (renewed != 1) {
            throw protocolFailure("renew returned an invalid status");
        }
        return Optional.of(new WorkerLeaseGrant(workerId, ownerId, redisTime));
    }

    @Override
    public boolean release(
            String environment,
            int workerId,
            String ownerId,
            long lastIssuedTimestamp) {
        validateEnvironment(environment);
        validateWorker(workerId);
        validateOwner(ownerId);
        Long result;
        try {
            result = redisTemplate.execute(
                    RELEASE_SCRIPT,
                    List.of(leaseKey(environment, workerId), watermarkKey(environment, workerId)),
                    ownerId,
                    Long.toString(lastIssuedTimestamp));
        } catch (RuntimeException exception) {
            throw new IdentifierUnavailableException("Redis worker lease release failed", exception);
        }
        if (result == null || (result != 0 && result != 1)) {
            throw protocolFailure("release returned an invalid status");
        }
        return result == 1;
    }

    private List<String> allWorkerKeys(String environment) {
        var keys = new ArrayList<String>(WORKER_COUNT * 2);
        for (int worker = 0; worker < WORKER_COUNT; worker++) {
            keys.add(leaseKey(environment, worker));
        }
        for (int worker = 0; worker < WORKER_COUNT; worker++) {
            keys.add(watermarkKey(environment, worker));
        }
        return keys;
    }

    private static String leaseKey(String environment, int workerId) {
        return keyPrefix(environment) + "lease:" + workerId;
    }

    private static String watermarkKey(String environment, int workerId) {
        return keyPrefix(environment) + "watermark:" + workerId;
    }

    private static String keyPrefix(String environment) {
        return "educloud:{" + environment + ":id-workers}:";
    }

    private List<?> executeList(
            String operation,
            DefaultRedisScript<List> script,
            List<String> keys,
            String... arguments) {
        try {
            List<?> result = redisTemplate.execute(script, keys, (Object[]) arguments);
            if (result == null) {
                throw protocolFailure(operation + " returned no result");
            }
            return result;
        } catch (IdentifierUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IdentifierUnavailableException(
                    "Redis worker lease " + operation + " failed",
                    exception);
        }
    }

    private static void requireSize(List<?> result, int expected, String operation) {
        if (result.size() != expected) {
            throw protocolFailure(operation + " returned an invalid result size");
        }
    }

    private static long requireLong(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw protocolFailure(field + " was not numeric");
        }
        return number.longValue();
    }

    private static void validateEnvironment(String environment) {
        if (environment == null || !environment.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException(
                    "environment must contain only letters, digits, dot, underscore or hyphen");
        }
    }

    private static void validateOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
    }

    private static void validateWorker(int workerId) {
        if (workerId < 0 || workerId >= WORKER_COUNT) {
            throw new IllegalArgumentException("workerId must be between 0 and 31");
        }
    }

    private static long validateTtl(Duration leaseTtl) {
        Objects.requireNonNull(leaseTtl, "leaseTtl");
        long ttlMillis;
        try {
            ttlMillis = leaseTtl.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("leaseTtl is too large", exception);
        }
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("leaseTtl must be at least one millisecond");
        }
        return ttlMillis;
    }

    private static IdentifierUnavailableException protocolFailure(String message) {
        return new IdentifierUnavailableException("Invalid Redis worker lease protocol: " + message);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static DefaultRedisScript<List> script(String name) {
        var script = new DefaultRedisScript<List>();
        script.setLocation(new ClassPathResource("com/educloud/common/id/" + name));
        script.setResultType(List.class);
        return script;
    }

    private static DefaultRedisScript<Long> longScript(String name) {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource("com/educloud/common/id/" + name));
        script.setResultType(Long.class);
        return script;
    }
}
