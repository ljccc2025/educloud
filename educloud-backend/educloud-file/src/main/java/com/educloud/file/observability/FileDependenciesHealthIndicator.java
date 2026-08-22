package com.educloud.file.observability;

import com.educloud.file.storage.StorageGateway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Objects;

/**
 * File 服务依赖就绪指示器（readiness 组 fileDependencies）。
 *
 * <p>依据：M04 设计规格第 12 节与实施计划任务 13 —— 四组依赖聚合：mysql（DataSource
 * 连接探测）、redis（ping）、rabbitmq（connection）、minio（{@link StorageGateway#probe()}
 * 轻量探测）；任一组 DOWN → 整体 DOWN；minio 探测失败/异常 → DOWN + 错误类别（只暴露
 * 类别，不泄漏内部地址/异常消息）。依赖未配置（如上下文测试排除 DataSource）时该组记
 * "未配置" 且不阻塞就绪决策。</p>
 */
@Component("fileDependencies")
public final class FileDependenciesHealthIndicator implements HealthIndicator {

    private static final String STATE_UP = "UP";
    private static final String STATE_DOWN = "DOWN";
    private static final String STATE_NOT_CONFIGURED = "未配置";
    private static final String MINIO_ERROR_CATEGORY_UNKNOWN = "UNKNOWN";

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final org.springframework.amqp.rabbit.connection.ConnectionFactory rabbitConnectionFactory;
    private final StorageGateway storageGateway;

    @Autowired
    public FileDependenciesHealthIndicator(
            ObjectProvider<DataSource> dataSourceProvider,
            ObjectProvider<RedisConnectionFactory> redisProvider,
            ObjectProvider<org.springframework.amqp.rabbit.connection.ConnectionFactory> rabbitProvider,
            StorageGateway storageGateway) {
        this(dataSourceProvider.getIfAvailable(),
                redisProvider.getIfAvailable(),
                rabbitProvider.getIfAvailable(),
                storageGateway);
    }

    FileDependenciesHealthIndicator(
            DataSource dataSource,
            RedisConnectionFactory redisConnectionFactory,
            org.springframework.amqp.rabbit.connection.ConnectionFactory rabbitConnectionFactory,
            StorageGateway storageGateway) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
        this.rabbitConnectionFactory = rabbitConnectionFactory;
        this.storageGateway = Objects.requireNonNull(storageGateway, "storageGateway");
    }

    @Override
    public Health health() {
        String mysql = checkMysql();
        String redis = checkRedis();
        String rabbit = checkRabbit();
        MinioCheck minio = checkMinio();

        boolean anyDown = STATE_DOWN.equals(mysql) || STATE_DOWN.equals(redis)
                || STATE_DOWN.equals(rabbit) || minio.down();

        Health.Builder builder = anyDown ? Health.down() : Health.up();
        builder.withDetail("mysql", mysql)
                .withDetail("redis", redis)
                .withDetail("rabbitmq", rabbit)
                .withDetail("minio", minio.status());
        if (minio.down() && minio.errorCategory() != null) {
            builder.withDetail("minioErrorCategory", minio.errorCategory());
        }
        return builder.build();
    }

    private String checkMysql() {
        if (dataSource == null) {
            return STATE_NOT_CONFIGURED;
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? STATE_UP : STATE_DOWN;
        } catch (Exception failure) {
            return STATE_DOWN;
        }
    }

    private String checkRedis() {
        if (redisConnectionFactory == null) {
            return STATE_NOT_CONFIGURED;
        }
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping()) ? STATE_UP : STATE_DOWN;
        } catch (Exception failure) {
            return STATE_DOWN;
        }
    }

    private String checkRabbit() {
        if (rabbitConnectionFactory == null) {
            return STATE_NOT_CONFIGURED;
        }
        try (org.springframework.amqp.rabbit.connection.Connection connection =
                     rabbitConnectionFactory.createConnection()) {
            return connection != null && connection.isOpen() ? STATE_UP : STATE_DOWN;
        } catch (Exception failure) {
            return STATE_DOWN;
        }
    }

    private MinioCheck checkMinio() {
        try {
            StorageGateway.StorageProbeResult probe = storageGateway.probe();
            if (probe.ok()) {
                return new MinioCheck(STATE_UP, null);
            }
            return new MinioCheck(STATE_DOWN, probe.errorCategory());
        } catch (Exception failure) {
            return new MinioCheck(STATE_DOWN, MINIO_ERROR_CATEGORY_UNKNOWN);
        }
    }

    /** minio 探测结果：状态 + 错误类别（DOWN 时类别用于对外展示）。 */
    private record MinioCheck(String status, String errorCategory) {

        boolean down() {
            return STATE_DOWN.equals(status);
        }
    }
}
