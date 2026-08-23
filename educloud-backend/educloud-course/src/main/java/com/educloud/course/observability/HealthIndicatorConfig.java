package com.educloud.course.observability;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.nacos.api.naming.NamingService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Objects;

/**
 * Course 服务依赖就绪指示器配置（readiness 组 courseDependencies）。
 *
 * <p>M05 任务 16：复刻 educloud-user UserDependenciesHealthIndicator /
 * educloud-file FileDependenciesHealthIndicator 模式 —— mysql（DataSource 连接探测）、
 * redis（ping）、rabbitmq（connection）、nacos（NamingService.getServerStatus）四组依赖
 * 聚合；任一组 DOWN → 整体 DOWN。依赖未配置（如 test profile 排除自动配置）时该组记
 * "未配置" 且不阻塞就绪决策。Bean 名 courseDependencies 由
 * application.yml 的 management.endpoint.health.group.readiness.include 引用。</p>
 */
@Configuration(proxyBeanMethods = false)
public class HealthIndicatorConfig {

    private static final String STATE_UP = "UP";
    private static final String STATE_DOWN = "DOWN";
    private static final String STATE_NOT_CONFIGURED = "未配置";

    @Bean("courseDependencies")
    HealthIndicator courseDependenciesHealthIndicator(
            ObjectProvider<DataSource> dataSourceProvider,
            ObjectProvider<RedisConnectionFactory> redisProvider,
            ObjectProvider<org.springframework.amqp.rabbit.connection.ConnectionFactory> rabbitProvider,
            ObjectProvider<NacosServiceManager> nacosServiceManagerProvider,
            ObjectProvider<NacosDiscoveryProperties> nacosDiscoveryPropertiesProvider) {
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        RedisConnectionFactory redisConnectionFactory = redisProvider.getIfAvailable();
        org.springframework.amqp.rabbit.connection.ConnectionFactory rabbitConnectionFactory =
                rabbitProvider.getIfAvailable();
        NacosServiceManager nacosServiceManager = nacosServiceManagerProvider.getIfAvailable();
        NacosDiscoveryProperties nacosDiscoveryProperties = nacosDiscoveryPropertiesProvider.getIfAvailable();
        return () -> {
            String mysql = checkMysql(dataSource);
            String redis = checkRedis(redisConnectionFactory);
            String rabbit = checkRabbit(rabbitConnectionFactory);
            String nacos = checkNacos(nacosServiceManager, nacosDiscoveryProperties);
            boolean anyDown = STATE_DOWN.equals(mysql) || STATE_DOWN.equals(redis)
                    || STATE_DOWN.equals(rabbit) || STATE_DOWN.equals(nacos);
            Health.Builder builder = anyDown ? Health.down() : Health.up();
            builder.withDetail("mysql", mysql)
                    .withDetail("redis", redis)
                    .withDetail("rabbitmq", rabbit)
                    .withDetail("nacos", nacos);
            return builder.build();
        };
    }

    private static String checkMysql(DataSource dataSource) {
        if (dataSource == null) {
            return STATE_NOT_CONFIGURED;
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? STATE_UP : STATE_DOWN;
        } catch (Exception failure) {
            return STATE_DOWN;
        }
    }

    private static String checkRedis(RedisConnectionFactory redisConnectionFactory) {
        if (redisConnectionFactory == null) {
            return STATE_NOT_CONFIGURED;
        }
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping()) ? STATE_UP : STATE_DOWN;
        } catch (Exception failure) {
            return STATE_DOWN;
        }
    }

    private static String checkRabbit(
            org.springframework.amqp.rabbit.connection.ConnectionFactory rabbitConnectionFactory) {
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

    private static String checkNacos(
            NacosServiceManager nacosServiceManager,
            NacosDiscoveryProperties nacosDiscoveryProperties) {
        if (nacosServiceManager == null || nacosDiscoveryProperties == null) {
            return STATE_NOT_CONFIGURED;
        }
        try {
            NamingService namingService = nacosServiceManager.getNamingService(
                    nacosDiscoveryProperties.getNacosProperties());
            String status = namingService.getServerStatus();
            return "UP".equalsIgnoreCase(Objects.requireNonNull(status, "nacos status")) ? STATE_UP : STATE_DOWN;
        } catch (Exception failure) {
            return STATE_DOWN;
        }
    }
}
