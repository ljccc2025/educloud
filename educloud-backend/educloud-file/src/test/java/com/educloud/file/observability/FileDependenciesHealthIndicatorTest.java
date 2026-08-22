package com.educloud.file.observability;

import com.educloud.file.storage.StorageGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * M04 任务 13：FileDependenciesHealthIndicator 单元测试。
 *
 * <p>依据：2026-08-22-educloud-file-plan.md 任务 13 —— mysql（DataSource 连接探测）、
 * redis（ping）、rabbit（connection）、minio（StorageGateway.probe 轻量探测）四组聚合；
 * 任一组 DOWN → 整体 DOWN；minio 探测失败/异常 → DOWN + 错误类别；未配置依赖不阻塞就绪。</p>
 */
class FileDependenciesHealthIndicatorTest {

    @Test
    void reportsUpWhenAllDependenciesAreAvailable() throws Exception {
        Fixture fixture = Fixture.allUp();

        Health health = new FileDependenciesHealthIndicator(
                fixture.dataSource, fixture.redisFactory, fixture.rabbitFactory, fixture.storageGateway)
                .health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsOnly(
                entry("mysql", "UP"),
                entry("redis", "UP"),
                entry("rabbitmq", "UP"),
                entry("minio", "UP"));
    }

    @Test
    void anyDependencyDownMakesOverallDown() throws Exception {
        Fixture mysqlFixture = Fixture.allUp().mysqlDown();
        Health mysqlDown = new FileDependenciesHealthIndicator(
                mysqlFixture.dataSource, mysqlFixture.redisFactory,
                mysqlFixture.rabbitFactory, mysqlFixture.storageGateway).health();
        assertDownWithDetails(mysqlDown, "mysql", "DOWN");

        Fixture redisFixture = Fixture.allUp().redisDown();
        Health redisDown = new FileDependenciesHealthIndicator(
                redisFixture.dataSource, redisFixture.redisFactory, redisFixture.rabbitFactory,
                redisFixture.storageGateway).health();
        assertDownWithDetails(redisDown, "redis", "DOWN");

        Fixture rabbitFixture = Fixture.allUp().rabbitDown();
        Health rabbitDown = new FileDependenciesHealthIndicator(
                rabbitFixture.dataSource, rabbitFixture.redisFactory, rabbitFixture.rabbitFactory,
                rabbitFixture.storageGateway).health();
        assertDownWithDetails(rabbitDown, "rabbitmq", "DOWN");

        Fixture minioFixture = Fixture.allUp().minioDown();
        Health minioDown = new FileDependenciesHealthIndicator(
                minioFixture.dataSource, minioFixture.redisFactory, minioFixture.rabbitFactory,
                minioFixture.storageGateway).health();
        assertDownWithDetails(minioDown, "minio", "DOWN");
    }

    @Test
    void minioProbeFailureReportsDownWithErrorCategory() throws Exception {
        StorageGateway storageGateway = mock(StorageGateway.class);
        when(storageGateway.probe())
                .thenReturn(new StorageGateway.StorageProbeResult(false, "CONNECTION"));
        Fixture fixture = Fixture.allUp();

        Health health = new FileDependenciesHealthIndicator(
                fixture.dataSource, fixture.redisFactory, fixture.rabbitFactory, storageGateway)
                .health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).contains(
                entry("minio", "DOWN"),
                entry("minioErrorCategory", "CONNECTION"));
        // 错误分类只暴露类别，不泄漏内部地址。
        assertThat(health.getDetails()).doesNotContainKeys("minioError", "minioAddress");
    }

    @Test
    void minioProbeExceptionDegradesToDownWithUnknownCategory() throws Exception {
        StorageGateway storageGateway = mock(StorageGateway.class);
        when(storageGateway.probe()).thenThrow(new IllegalStateException("minio connection refused"));
        Fixture fixture = Fixture.allUp();

        Health health = new FileDependenciesHealthIndicator(
                fixture.dataSource, fixture.redisFactory, fixture.rabbitFactory, storageGateway)
                .health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).contains(
                entry("minio", "DOWN"),
                entry("minioErrorCategory", "UNKNOWN"));
        assertThat(health.getDetails().toString()).doesNotContain("connection refused");
    }

    @Test
    void missingDataSourceReportsNotConfiguredWithoutFailingReadiness() throws Exception {
        Fixture fixture = Fixture.allUp();

        Health health = new FileDependenciesHealthIndicator(
                null, fixture.redisFactory, fixture.rabbitFactory, fixture.storageGateway)
                .health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).contains(
                entry("mysql", "未配置"),
                entry("redis", "UP"),
                entry("rabbitmq", "UP"),
                entry("minio", "UP"));
    }

    private static void assertDownWithDetails(Health health, String failedDependency, String state) {
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).contains(entry(failedDependency, state));
    }

    /** 全 UP 夹具；各依赖可单独降级为 DOWN。 */
    private static final class Fixture {
        DataSource dataSource;
        RedisConnectionFactory redisFactory;
        org.springframework.amqp.rabbit.connection.ConnectionFactory rabbitFactory;
        StorageGateway storageGateway;

        static Fixture allUp() throws Exception {
            Fixture fixture = new Fixture();

            Connection jdbc = mock(Connection.class);
            when(jdbc.isValid(2)).thenReturn(true);
            fixture.dataSource = mock(DataSource.class);
            when(fixture.dataSource.getConnection()).thenReturn(jdbc);

            RedisConnection redis = mock(RedisConnection.class);
            when(redis.ping()).thenReturn("PONG");
            fixture.redisFactory = mock(RedisConnectionFactory.class);
            when(fixture.redisFactory.getConnection()).thenReturn(redis);

            org.springframework.amqp.rabbit.connection.Connection rabbit =
                    mock(org.springframework.amqp.rabbit.connection.Connection.class);
            when(rabbit.isOpen()).thenReturn(true);
            fixture.rabbitFactory =
                    mock(org.springframework.amqp.rabbit.connection.ConnectionFactory.class);
            when(fixture.rabbitFactory.createConnection()).thenReturn(rabbit);

            fixture.storageGateway = mock(StorageGateway.class);
            when(fixture.storageGateway.probe())
                    .thenReturn(new StorageGateway.StorageProbeResult(true, null));
            return fixture;
        }

        Fixture mysqlDown() throws Exception {
            Connection jdbc = mock(Connection.class);
            when(jdbc.isValid(2)).thenReturn(false);
            dataSource = mock(DataSource.class);
            when(dataSource.getConnection()).thenReturn(jdbc);
            return this;
        }

        Fixture redisDown() throws Exception {
            RedisConnection redis = mock(RedisConnection.class);
            when(redis.ping()).thenReturn("NOPE");
            redisFactory = mock(RedisConnectionFactory.class);
            when(redisFactory.getConnection()).thenReturn(redis);
            return this;
        }

        Fixture rabbitDown() throws Exception {
            org.springframework.amqp.rabbit.connection.Connection rabbit =
                    mock(org.springframework.amqp.rabbit.connection.Connection.class);
            when(rabbit.isOpen()).thenReturn(false);
            rabbitFactory = mock(org.springframework.amqp.rabbit.connection.ConnectionFactory.class);
            when(rabbitFactory.createConnection()).thenReturn(rabbit);
            return this;
        }

        Fixture minioDown() throws Exception {
            storageGateway = mock(StorageGateway.class);
            when(storageGateway.probe())
                    .thenReturn(new StorageGateway.StorageProbeResult(false, "IO"));
            return this;
        }
    }
}
