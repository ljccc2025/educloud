package com.educloud.user.observability;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.NacosServiceManager;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Objects;

/**
 * User 服务依赖就绪指示器（readiness 组 userDependencies）。
 * 依据：M03 设计规格第 12 节（MySQL/Redis/RabbitMQ/Nacos 可达；
 * 认证/授权不能因依赖故障失败开放，就绪状态如实上报）。
 */
@Component
public final class UserDependenciesHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final org.springframework.amqp.rabbit.connection.ConnectionFactory rabbitConnectionFactory;
    private final NacosServiceManager nacosServiceManager;
    private final NacosDiscoveryProperties nacosDiscoveryProperties;

    public UserDependenciesHealthIndicator(
            DataSource dataSource,
            RedisConnectionFactory redisConnectionFactory,
            org.springframework.amqp.rabbit.connection.ConnectionFactory rabbitConnectionFactory,
            NacosServiceManager nacosServiceManager,
            NacosDiscoveryProperties nacosDiscoveryProperties) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.redisConnectionFactory = Objects.requireNonNull(redisConnectionFactory, "redisConnectionFactory");
        this.rabbitConnectionFactory = Objects.requireNonNull(rabbitConnectionFactory, "rabbitConnectionFactory");
        this.nacosServiceManager = Objects.requireNonNull(nacosServiceManager, "nacosServiceManager");
        this.nacosDiscoveryProperties = Objects.requireNonNull(nacosDiscoveryProperties, "nacosDiscoveryProperties");
    }

    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        builder.up().withDetail("mysql", check("mysql", this::checkMysql))
                .withDetail("redis", check("redis", this::checkRedis))
                .withDetail("rabbitmq", check("rabbitmq", this::checkRabbit))
                .withDetail("nacos", check("nacos", this::checkNacos));
        if (builder.build().getDetails().values().stream().anyMatch("DOWN"::equals)) {
            builder.down();
        }
        return builder.build();
    }

    private String check(String name, java.util.function.BooleanSupplier probe) {
        try {
            return probe.getAsBoolean() ? "UP" : "DOWN";
        } catch (Exception failure) {
            return "DOWN";
        }
    }

    private boolean checkMysql() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception failure) {
            return false;
        }
    }

    private boolean checkRedis() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping());
        }
    }

    private boolean checkRabbit() {
        try (org.springframework.amqp.rabbit.connection.Connection connection =
                     rabbitConnectionFactory.createConnection()) {
            return connection != null && connection.isOpen();
        }
    }

    private boolean checkNacos() {
        try {
            NamingService namingService = nacosServiceManager.getNamingService(
                    nacosDiscoveryProperties.getNacosProperties());
            String status = namingService.getServerStatus();
            return "UP".equalsIgnoreCase(status);
        } catch (Exception failure) {
            return false;
        }
    }
}
