package com.educloud.course.observability;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.NacosServiceManager;
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

/**
 * M05 任务 16 + 审查建议：HealthIndicatorConfig 就绪指示器行为测试。
 *
 * <p>无任何依赖 provider → 整体 UP 且各依赖 detail 为「未配置」（不阻塞就绪决策）；
 * 任一依赖探测 DOWN → 整体 DOWN。与 FileDependenciesHealthIndicatorTest 同思路。</p>
 */
class HealthIndicatorConfigTest {

    private final HealthIndicatorConfig config = new HealthIndicatorConfig();

    @Test
    void reportsUpWithNotConfiguredDetailsWhenNoProvidersPresent() {
        Health health = config.courseDependenciesHealthIndicator(
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

        Health health = config.courseDependenciesHealthIndicator(
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

        Health health = config.courseDependenciesHealthIndicator(
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
