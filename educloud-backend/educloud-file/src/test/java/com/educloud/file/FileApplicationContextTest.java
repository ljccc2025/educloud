package com.educloud.file;

import com.educloud.file.config.FileProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * M04 计划任务 0：最小上下文启动测试（禁用全部外部连接）。
 *
 * <p>依据：M04 实施计划任务 0 步骤 1-3（先写上下文测试，无实现时失败）。
 * 外部依赖（MySQL/Redis/RabbitMQ/Nacos）在测试中通过自动配置排除与开关关闭；
 * StringRedisTemplate/ConnectionFactory/MinioClient 以 mock bean 满足外部连接依赖
 * （mock 使用独立 bean 名，避免与 FileStorageConfiguration 的 minioClient 同名冲突；
 * MinioClient 构建本身为懒连接、不发网络请求）。Outbox 相关 Mapper 属后续任务
 * （任务 11 Outbox 事件发布），任务 0 尚无对应类，故不在本测试中 mock。</p>
 */
class FileApplicationContextTest {

    private static final String EXCLUDED_AUTOCONFIGURATIONS = String.join(",",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
            "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
            "com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration",
            "org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClientAutoConfiguration");

    @Test
    void startsTheCompleteFileContextWithoutExternalConnections() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                FileApplication.class, ExternalDependencyConfiguration.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--management.server.address=127.0.0.1",
                        "--management.server.port=0",
                        "--spring.main.banner-mode=off",
                        "--spring.autoconfigure.exclude=" + EXCLUDED_AUTOCONFIGURATIONS,
                        "--spring.cloud.nacos.discovery.enabled=false",
                        "--spring.cloud.nacos.config.enabled=false",
                        "--spring.cloud.nacos.discovery.register-enabled=false",
                        "--management.tracing.sampling.probability=0.0",
                        "--management.endpoint.health.group.readiness.include=readinessState",
                        "--educloud.file.storage.endpoint=http://127.0.0.1:9000",
                        "--educloud.file.storage.access-key=context-test-access",
                        "--educloud.file.storage.secret-key=context-test-secret",
                        "--educloud.file.storage.bucket=educloud-files-context-test",
                        "--educloud.file.storage.init-bucket-on-startup=false")) {
            assertThat(context.isActive()).isTrue();
            assertThat(context.getBean(FileProperties.class).storage().endpoint())
                    .isEqualTo("http://127.0.0.1:9000");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExternalDependencyConfiguration {

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        org.springframework.amqp.rabbit.connection.ConnectionFactory rabbitConnectionFactory() {
            return mock(org.springframework.amqp.rabbit.connection.ConnectionFactory.class);
        }

        @Bean
        MinioClient minioClientMock() {
            return mock(MinioClient.class);
        }
    }
}
