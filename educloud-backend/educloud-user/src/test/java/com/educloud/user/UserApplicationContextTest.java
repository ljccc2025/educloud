package com.educloud.user;

import com.educloud.user.mapper.AuditEventMapper;
import com.educloud.user.mapper.IdempotencyRecordMapper;
import com.educloud.user.mapper.InboxEventMapper;
import com.educloud.user.mapper.LoginAuditMapper;
import com.educloud.user.mapper.OutboxEventMapper;
import com.educloud.user.mapper.OutboxSequenceMapper;
import com.educloud.user.mapper.PlatformPublicConfigMapper;
import com.educloud.user.mapper.RefreshSessionMapper;
import com.educloud.user.mapper.ServiceClientCredentialMapper;
import com.educloud.user.mapper.ServiceClientMapper;
import com.educloud.user.mapper.SysPermissionMapper;
import com.educloud.user.mapper.SysRoleMapper;
import com.educloud.user.mapper.SysRolePermissionMapper;
import com.educloud.user.mapper.SysUserMapper;
import com.educloud.user.mapper.SysUserRoleMapper;
import com.educloud.user.mapper.UserProfileMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * M03 计划任务 0：最小上下文启动测试（禁用全部外部连接）。
 *
 * <p>依据：M03 实施计划任务 0 步骤 3（先写上下文测试，无实现时失败）。
 * 外部依赖（MySQL/Redis/RabbitMQ/Nacos）在测试中通过自动配置排除与开关关闭；
 * 数据访问层以 mock bean 满足依赖（MyBatis-Plus Mapper 在无 DataSource 时不注册）；
 * JwtKeyProvider 需要私钥文件，测试内临时生成（与 JwtKeyProviderTest 同法）。</p>
 */
class UserApplicationContextTest {

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
    void startsTheCompleteUserContextWithoutExternalConnections() throws Exception {
        String privateKeyLocation = writePkcs8Key();
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                UserApplication.class, ExternalDependencyConfiguration.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--spring.main.banner-mode=off",
                        "--spring.autoconfigure.exclude=" + EXCLUDED_AUTOCONFIGURATIONS,
                        "--spring.cloud.nacos.discovery.enabled=false",
                        "--spring.cloud.nacos.config.enabled=false",
                        "--spring.cloud.nacos.discovery.register-enabled=false",
                        "--management.tracing.sampling.probability=0.0",
                        "--management.endpoint.health.group.readiness.include=readinessState",
                        "--educloud.user.session.environment=context-test",
                        "--educloud.user.jwt.private-key-location=" + privateKeyLocation,
                        "--educloud.user.jwt.issuer=https://issuer.educloud.local",
                        "--educloud.user.jwt.audience=educloud-api")) {
            assertThat(context.isActive()).isTrue();
        }
    }

    private String writePkcs8Key() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) pair.getPrivate();
        String base64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + wrap(base64) + "-----END PRIVATE KEY-----\n";
        Path keyFile = tempDir.resolve("context-private.pem");
        Files.write(keyFile, pem.getBytes(StandardCharsets.US_ASCII));
        return keyFile.toString();
    }

    private static String wrap(String base64) {
        StringBuilder wrapped = new StringBuilder();
        for (int index = 0; index < base64.length(); index += 64) {
            wrapped.append(base64, index, Math.min(index + 64, base64.length())).append('\n');
        }
        return wrapped.toString();
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
        com.educloud.user.observability.UserDependenciesHealthIndicator userDependenciesHealthIndicator() {
            return mock(com.educloud.user.observability.UserDependenciesHealthIndicator.class);
        }


        @Bean
        SysUserMapper sysUserMapper() {
            return mock(SysUserMapper.class);
        }

        @Bean
        UserProfileMapper userProfileMapper() {
            return mock(UserProfileMapper.class);
        }

        @Bean
        SysRoleMapper sysRoleMapper() {
            return mock(SysRoleMapper.class);
        }

        @Bean
        SysPermissionMapper sysPermissionMapper() {
            return mock(SysPermissionMapper.class);
        }

        @Bean
        SysUserRoleMapper sysUserRoleMapper() {
            return mock(SysUserRoleMapper.class);
        }

        @Bean
        SysRolePermissionMapper sysRolePermissionMapper() {
            return mock(SysRolePermissionMapper.class);
        }

        @Bean
        RefreshSessionMapper refreshSessionMapper() {
            return mock(RefreshSessionMapper.class);
        }

        @Bean
        ServiceClientMapper serviceClientMapper() {
            return mock(ServiceClientMapper.class);
        }

        @Bean
        ServiceClientCredentialMapper serviceClientCredentialMapper() {
            return mock(ServiceClientCredentialMapper.class);
        }

        @Bean
        PlatformPublicConfigMapper platformPublicConfigMapper() {
            return mock(PlatformPublicConfigMapper.class);
        }

        @Bean
        LoginAuditMapper loginAuditMapper() {
            return mock(LoginAuditMapper.class);
        }

        @Bean
        AuditEventMapper auditEventMapper() {
            return mock(AuditEventMapper.class);
        }

        @Bean
        OutboxEventMapper outboxEventMapper() {
            return mock(OutboxEventMapper.class);
        }

        @Bean
        OutboxSequenceMapper outboxSequenceMapper() {
            return mock(OutboxSequenceMapper.class);
        }

        @Bean
        InboxEventMapper inboxEventMapper() {
            return mock(InboxEventMapper.class);
        }

        @Bean
        IdempotencyRecordMapper idempotencyRecordMapper() {
            return mock(IdempotencyRecordMapper.class);
        }
    }
}
