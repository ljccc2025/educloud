package com.educloud.file;

import com.educloud.file.config.FileProperties;
import com.educloud.file.mapper.FileAccessAuditMapper;
import com.educloud.file.mapper.FileBindingMapper;
import com.educloud.file.mapper.FileObjectMapper;
import com.educloud.file.mapper.FileUploadSessionMapper;
import com.educloud.file.mapper.OutboxEventMapper;
import com.educloud.file.mapper.OutboxSequenceMapper;
import com.educloud.file.security.TestJwtKeys;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * M04 计划任务 0：最小上下文启动测试（禁用全部外部连接）。
 *
 * <p>依据：M04 实施计划任务 0 步骤 1-3（先写上下文测试，无实现时失败）。
 * 外部依赖（MySQL/Redis/RabbitMQ/Nacos）在测试中通过自动配置排除与开关关闭；
 * StringRedisTemplate/ConnectionFactory/MinioClient 以 mock bean 满足外部连接依赖
 * （mock 使用独立 bean 名，避免与 FileStorageConfiguration 的 minioClient 同名冲突；
 * MinioClient 构建本身为懒连接、不发网络请求）。MyBatis-Plus Mapper 在无 DataSource
 * 时不注册，故 UploadSessionService 依赖的 Mapper 以 mock bean 满足（与
 * UserApplicationContextTest 同法）。任务 11 起 Outbox 相关 Mapper
 * （OutboxEventMapper/OutboxSequenceMapper）与 RabbitMQ ConnectionFactory
 * 一并 mock，保证 RabbitConfiguration/OutboxEventDispatcher 可无外部连接启动。任务 12 起
 * FileCleanupService 的 PlatformTransactionManager 一并 mock（无 DataSource 自动配置）。</p>
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

    @TempDir
    Path tempDir;

    @Test
    void startsTheCompleteFileContextWithoutExternalConnections() throws Exception {
        // 任务 8 起 JwtDecoderConfiguration 启动时静态加载 User 公钥 JWKS：测试内生成临时
        // 密钥对与 JWKS 文件（与 SecurityConfigurationTest 同法），保持无外部连接。
        Path jwksFile = tempDir.resolve("test-jwks.json");
        Files.writeString(jwksFile, new TestJwtKeys().publicJwksJson());
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
                        "--educloud.file.storage.init-bucket-on-startup=false",
                        "--educloud.file.internal.allowed-client-ids=user-service",
                        "--educloud.file.internal.audience=educloud-file",
                        "--educloud.file.jwt.jwks-location=file:" + jwksFile.toAbsolutePath(),
                        "--educloud.file.jwt.issuer=https://issuer.educloud.local",
                        "--educloud.file.jwt.audience=educloud-api")) {
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

        @Bean
        org.springframework.transaction.PlatformTransactionManager platformTransactionManager() {
            // 任务 12 的 FileCleanupService 需要事务管理器（DataSource 自动配置被排除）。
            return mock(org.springframework.transaction.PlatformTransactionManager.class);
        }

        @Bean
        FileUploadSessionMapper fileUploadSessionMapper() {
            return mock(FileUploadSessionMapper.class);
        }

        @Bean
        FileObjectMapper fileObjectMapper() {
            return mock(FileObjectMapper.class);
        }

        @Bean
        FileBindingMapper fileBindingMapper() {
            return mock(FileBindingMapper.class);
        }

        @Bean
        FileAccessAuditMapper fileAccessAuditMapper() {
            return mock(FileAccessAuditMapper.class);
        }

        @Bean
        OutboxEventMapper outboxEventMapper() {
            return mock(OutboxEventMapper.class);
        }

        @Bean
        OutboxSequenceMapper outboxSequenceMapper() {
            return mock(OutboxSequenceMapper.class);
        }
    }
}
