package com.educloud.common.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class RedisIdentifierConcurrencyIT {

    @Test
    void twoManagersGenerateOneHundredThousandUniqueIdentifiers() throws Exception {
        try (var redis = redisContainer()) {
            redis.start();
            var connection = connection(redis);
            try {
                var repository = new RedisWorkerLeaseRepository(template(connection));
                String environment = environment();
                var firstManager = new WorkerLeaseManager(
                        repository, environment, Duration.ofSeconds(30), Duration.ofSeconds(10));
                var secondManager = new WorkerLeaseManager(
                        repository, environment, Duration.ofSeconds(30), Duration.ofSeconds(10));
                firstManager.start();
                secondManager.start();
                try {
                    assertThat(firstManager.requireActiveWorkerId())
                            .isNotEqualTo(secondManager.requireActiveWorkerId());
                    var firstGenerator = generator(firstManager);
                    var secondGenerator = generator(secondManager);
                    Set<Long> identifiers = ConcurrentHashMap.newKeySet(100_000);
                    var executor = Executors.newFixedThreadPool(2);
                    try {
                        Future<?> first = executor.submit(
                                () -> generate(firstGenerator, identifiers, 50_000));
                        Future<?> second = executor.submit(
                                () -> generate(secondGenerator, identifiers, 50_000));
                        first.get();
                        second.get();
                    } finally {
                        executor.shutdownNow();
                    }
                    assertThat(identifiers).hasSize(100_000);
                } finally {
                    firstManager.close();
                    secondManager.close();
                }
            } finally {
                connection.destroy();
            }
        }
    }

    @Test
    void redisInterruptionCausesGenerationToFailClosedAtTheLocalDeadline() {
        var redis = redisContainer();
        redis.start();
        var connection = connection(redis);
        var repository = new RedisWorkerLeaseRepository(template(connection));
        var manager = new WorkerLeaseManager(
                repository,
                environment(),
                Duration.ofMillis(500),
                Duration.ofMillis(100));
        manager.start();
        var generator = generator(manager);
        assertThat(generator.nextId()).isPositive();

        redis.stop();

        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThatThrownBy(generator::nextId)
                        .isInstanceOf(IdentifierUnavailableException.class));
        connection.destroy();
    }

    private static WorkerLeaseIdentifierGenerator generator(WorkerLeaseManager manager) {
        return new WorkerLeaseIdentifierGenerator(
                manager,
                Clock.systemUTC(),
                duration -> Thread.sleep(duration.toMillis()),
                Duration.ofMillis(5));
    }

    private static void generate(
            IdentifierGenerator generator,
            Set<Long> identifiers,
            int count) {
        for (int index = 0; index < count; index++) {
            assertThat(identifiers.add(generator.nextId())).isTrue();
        }
    }

    private static GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.2.5-alpine"))
                .withExposedPorts(6379);
    }

    private static LettuceConnectionFactory connection(GenericContainer<?> redis) {
        var factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(6379)));
        factory.afterPropertiesSet();
        return factory;
    }

    private static StringRedisTemplate template(LettuceConnectionFactory factory) {
        var template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    private static String environment() {
        return "it-" + UUID.randomUUID();
    }
}
