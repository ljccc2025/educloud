package com.educloud.common.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisWorkerLeaseRepositoryIT {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
                    DockerImageName.parse("redis:7.2.5-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisWorkerLeaseRepository repository;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        repository = new RedisWorkerLeaseRepository(redisTemplate);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @Test
    void allocatesThirtyTwoSlotsAndRejectsTheThirtyThird() {
        String environment = environment();
        var grants = new ArrayList<WorkerLeaseGrant>();

        for (int workerId = 0; workerId < 32; workerId++) {
            var grant = repository.tryAcquire(
                    environment, UUID.randomUUID().toString(), Duration.ofSeconds(30));
            assertThat(grant).isPresent();
            grants.add(grant.orElseThrow());
        }

        assertThat(grants).extracting(WorkerLeaseGrant::workerId).containsExactlyElementsOf(
                java.util.stream.IntStream.range(0, 32).boxed().toList());
        assertThat(repository.tryAcquire(
                        environment, UUID.randomUUID().toString(), Duration.ofSeconds(30)))
                .isEmpty();
    }

    @Test
    void protectsOwnershipAcrossRenewalAndRelease() {
        String environment = environment();
        String owner = UUID.randomUUID().toString();
        WorkerLeaseGrant grant = repository.tryAcquire(
                        environment, owner, Duration.ofSeconds(5))
                .orElseThrow();

        assertThat(repository.renew(
                        environment,
                        grant.workerId(),
                        "wrong-owner",
                        Duration.ofSeconds(5),
                        grant.redisTimeMillis()))
                .isEmpty();
        assertThat(repository.release(
                        environment,
                        grant.workerId(),
                        "wrong-owner",
                        grant.redisTimeMillis() + 1))
                .isFalse();
        assertThat(redisTemplate.opsForValue().get(watermarkKey(environment, grant.workerId())))
                .isNull();

        assertThat(repository.renew(
                        environment,
                        grant.workerId(),
                        owner,
                        Duration.ofSeconds(5),
                        grant.redisTimeMillis() + 1))
                .isPresent();
        assertThat(repository.release(
                        environment,
                        grant.workerId(),
                        owner,
                        grant.redisTimeMillis() + 2))
                .isTrue();
        assertThat(redisTemplate.opsForValue().get(watermarkKey(environment, grant.workerId())))
                .isEqualTo(Long.toString(grant.redisTimeMillis() + 2));
    }

    @Test
    void releaseWatermarkPreventsImmediateReuseAndTtlAllowsExpiredReuse() {
        String watermarkEnvironment = environment();
        String owner = UUID.randomUUID().toString();
        WorkerLeaseGrant grant = repository.tryAcquire(
                        watermarkEnvironment, owner, Duration.ofSeconds(5))
                .orElseThrow();
        long futureWatermark = grant.redisTimeMillis() + 2_000;

        assertThat(repository.release(
                        watermarkEnvironment,
                        grant.workerId(),
                        owner,
                        futureWatermark))
                .isTrue();
        assertThat(repository.tryAcquire(
                        watermarkEnvironment,
                        UUID.randomUUID().toString(),
                        Duration.ofSeconds(5)))
                .get()
                .extracting(WorkerLeaseGrant::workerId)
                .isNotEqualTo(grant.workerId());

        String ttlEnvironment = environment();
        WorkerLeaseGrant expiring = repository.tryAcquire(
                        ttlEnvironment,
                        UUID.randomUUID().toString(),
                        Duration.ofMillis(250))
                .orElseThrow();
        assertThat(redisTemplate.getExpire(
                        leaseKey(ttlEnvironment, expiring.workerId()),
                        java.util.concurrent.TimeUnit.MILLISECONDS))
                .isPositive();

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(
                        repository.tryAcquire(
                                ttlEnvironment,
                                UUID.randomUUID().toString(),
                                Duration.ofMillis(250)))
                .get()
                .extracting(WorkerLeaseGrant::workerId)
                .isEqualTo(expiring.workerId()));
    }

    private static String environment() {
        return "it-" + UUID.randomUUID();
    }

    private static String leaseKey(String environment, int workerId) {
        return "educloud:{" + environment + ":id-workers}:lease:" + workerId;
    }

    private static String watermarkKey(String environment, int workerId) {
        return "educloud:{" + environment + ":id-workers}:watermark:" + workerId;
    }
}
