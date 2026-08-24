package com.educloud.order.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthIndicatorConfigTest {

    private final HealthIndicatorConfig config = new HealthIndicatorConfig();

    @Test
    void reportsUpWithNotConfiguredDetailsWhenNoProvidersPresent() {
        Health health = config.orderDependenciesHealthIndicator(
                providerOf(null), providerOf(null), providerOf(null),
                providerOf(null), providerOf(null)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("mysql", "未配置")
                .containsEntry("redis", "未配置")
                .containsEntry("rabbitmq", "未配置")
                .containsEntry("nacos", "未配置");
    }

    @Test
    void reportsDownWhenMysqlProbeFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("mysql unreachable"));

        Health health = config.orderDependenciesHealthIndicator(
                providerOf(dataSource), providerOf(null), providerOf(null),
                providerOf(null), providerOf(null)).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("mysql", "DOWN")
                .containsEntry("redis", "未配置");
    }

    @Test
    void reportsDownWhenRedisProbeFails() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenThrow(new RuntimeException("redis unreachable"));

        Health health = config.orderDependenciesHealthIndicator(
                providerOf(null), providerOf(factory), providerOf(null),
                providerOf(null), providerOf(null)).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("redis", "DOWN");
    }

    private static <T> ObjectProvider<T> providerOf(T value) {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
