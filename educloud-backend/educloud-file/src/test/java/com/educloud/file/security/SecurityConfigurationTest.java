package com.educloud.file.security;

import com.educloud.common.api.ApiResponse;
import com.educloud.file.FileApplication;
import com.educloud.file.mapper.FileAccessAuditMapper;
import com.educloud.file.mapper.FileBindingMapper;
import com.educloud.file.mapper.FileObjectMapper;
import com.educloud.file.mapper.FileUploadSessionMapper;
import io.minio.MinioClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务 8：Resource Server 安全链集成测试（真实 JWKS 验签 + 方法安全）。
 *
 * <p>复用 user MethodSecurityAndAdminEndpointsTest 的完整上下文启动模式（禁用外部
 * 连接 + mock 外部依赖）：测试内生成临时 RSA 密钥对，JWKS 文件供
 * JwtDecoderConfiguration 加载，私钥签发用户令牌。断言：无 token 401 信封；
 * 带正确签名 + permissions 权限码的 token 放行受保护端点；无权限码 403；
 * 错误 aud 401。（actuator 按 application.yml 运行在独立 management 端口 8088，
 * 不在主 SecurityFilterChain 作用域内，故不在本测试断言匿名可达。）</p>
 */
class SecurityConfigurationTest {

    private static final String ISSUER = "https://issuer.educloud.local";
    private static final String EXCLUDED_AUTOCONFIGURATIONS = String.join(",",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
            "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
            "com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration",
            "org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClientAutoConfiguration");

    private static ConfigurableApplicationContext context;
    private static MockMvc mockMvc;
    private static TestJwtKeys testKeys;

    @BeforeAll
    static void startContext() throws Exception {
        testKeys = new TestJwtKeys();
        Path jwksFile = Files.createTempFile("file-security-jwks-", ".json");
        Files.writeString(jwksFile, testKeys.publicJwksJson());
        context = new SpringApplicationBuilder(FileApplication.class, SecurityTestConfiguration.class)
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
                        "--educloud.file.storage.access-key=security-test-access",
                        "--educloud.file.storage.secret-key=security-test-secret",
                        "--educloud.file.storage.bucket=educloud-files-security-test",
                        "--educloud.file.storage.init-bucket-on-startup=false",
                        "--educloud.file.internal.allowed-client-ids=user-service",
                        "--educloud.file.internal.audience=educloud-file",
                        "--educloud.file.jwt.jwks-location=file:" + jwksFile.toAbsolutePath(),
                        "--educloud.file.jwt.issuer=" + ISSUER,
                        "--educloud.file.jwt.audience=educloud-api",
                        "--educloud.file.storage-test.rate-limit=1",
                        "--educloud.file.storage-test.window=1m");
        mockMvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @AfterAll
    static void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void rejectsMissingTokenWith401Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/files/security-probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void allowsUserTokenWithRequiredPermission() throws Exception {
        mockMvc.perform(get("/api/v1/files/security-probe")
                        .header("Authorization", "Bearer " + userToken("educloud-api", List.of("file:probe"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("probe-ok"));
    }

    @Test
    void rejectsUserTokenWithoutRequiredPermission() throws Exception {
        mockMvc.perform(get("/api/v1/files/security-probe")
                        .header("Authorization", "Bearer " + userToken("educloud-api", List.of("file:other"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_ACCESS_DENIED"));
    }

    @Test
    void rejectsUserTokenWithWrongAudience() throws Exception {
        mockMvc.perform(get("/api/v1/files/security-probe")
                        .header("Authorization", "Bearer " + userToken("educloud-user", List.of("file:probe"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private static String userToken(String audience, List<String> permissions) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", ISSUER);
        claims.put("aud", List.of(audience));
        claims.put("exp", now.plusSeconds(300));
        claims.put("nbf", now.minusSeconds(1));
        claims.put("iat", now.minusSeconds(1));
        claims.put("sub", "1001");
        claims.put("sid", "session-1001");
        claims.put("tokenVersion", 1L);
        claims.put("userType", "STUDENT");
        claims.put("roles", List.of("STUDENT"));
        claims.put("permissions", permissions);
        return testKeys.signedToken(claims);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityTestConfiguration {

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
    }
}
