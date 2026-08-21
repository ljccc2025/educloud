package com.educloud.user;

import com.educloud.common.api.PageResponse;
import com.educloud.user.dto.response.RoleResponse;
import com.educloud.user.service.AuthenticationService;
import com.educloud.user.service.IdempotencyService;
import com.educloud.user.service.PlatformConfigService;
import com.educloud.user.service.ProfileService;
import com.educloud.user.service.RefreshSessionService;
import com.educloud.user.service.RegistrationService;
import com.educloud.user.service.RoleService;
import com.educloud.user.service.SessionRevocationService;
import com.educloud.user.service.SigningKeyStatusService;
import com.educloud.user.service.UserAdminService;
import com.educloud.user.service.UserStatusService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 方法安全与管理端点集成测试（真实 SecurityFilterChain + @PreAuthorize）。
 * 依据：M03 计划任务 12（rbac/user/platform/security 权限码方法授权、匿名公开配置）。
 */
class MethodSecurityAndAdminEndpointsTest {

    private static ConfigurableApplicationContext context;
    private static MockMvc mockMvc;

    @BeforeAll
    static void startContext() throws Exception {
        String keyFile = writePkcs8Key();
        context = new SpringApplicationBuilder(UserApplication.class, MockServices.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--spring.main.banner-mode=off",
                        "--spring.autoconfigure.exclude="
                                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,"
                                + "com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration,"
                                + "org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClientAutoConfiguration",
                        "--spring.cloud.nacos.discovery.enabled=false",
                        "--spring.cloud.nacos.config.enabled=false",
                        "--spring.cloud.nacos.discovery.register-enabled=false",
                        "--management.tracing.sampling.probability=0.0",
                        "--management.endpoint.health.group.readiness.include=readinessState",
                        "--educloud.user.session.environment=test",
                        "--educloud.user.jwt.private-key-location=" + keyFile,
                        "--educloud.user.jwt.issuer=https://issuer.educloud.local",
                        "--educloud.user.jwt.audience=educloud-api");

        mockMvc = MockMvcBuilders.webAppContextSetup(
                (org.springframework.web.context.WebApplicationContext) context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        when(context.getBean(UserAdminService.class).page(1, 20))
                .thenReturn(PageResponse.of(List.of(), 1, 20, 0));
        when(context.getBean(RoleService.class).create(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RoleResponse("9", "TUTOR", "助教", null, "ACTIVE", false));
        when(context.getBean(PlatformConfigService.class).publicConfigs())
                .thenReturn(List.of());
        when(context.getBean(SigningKeyStatusService.class).status())
                .thenReturn(new com.educloud.user.dto.response.SigningKeyStatusResponse(
                        "educloud-user-testkid", 1, java.time.Instant.now(), null));
    }

    @AfterAll
    static void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void adminUserListRequiresUserReadAuthority() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users").with(jwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("user:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void statusUpdateRequiresUserStatusUpdateAuthority() throws Exception {
        mockMvc.perform(patch("/api/v1/users/1001/status")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\",\"version\":1}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/users/1001/status")
                        .with(jwt().authorities(new SimpleGrantedAuthority("user:status:update")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\",\"version\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    void roleAndPermissionEndpointsRequireRbacAuthorities() throws Exception {
        mockMvc.perform(get("/api/v1/roles").with(jwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/roles")
                        .with(jwt().authorities(new SimpleGrantedAuthority("rbac:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"TUTOR\",\"name\":\"助教\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/permissions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("rbac:read"))))
                .andExpect(status().isOk());
    }

    @Test
    void publicConfigIsAnonymousButUpdateRequiresAuthority() throws Exception {
        mockMvc.perform(get("/api/v1/platform-config/public"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/platform-config/public")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configKey\":\"site_name\",\"configValue\":\"x\",\"valueType\":\"STRING\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/platform-config/public")
                        .with(jwt().authorities(new SimpleGrantedAuthority("platform:config:update")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configKey\":\"site_name\",\"configValue\":\"x\",\"valueType\":\"STRING\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void passwordChangeRequiresAuthenticationAndUpdatesCurrentFamily() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"old-pass\",\"newPassword\":\"new-pass-123\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(jwt().jwt(jwt -> jwt.subject("1001")))
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "raw-refresh-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"old-pass\",\"newPassword\":\"new-pass-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
        org.mockito.Mockito.verify(context.getBean(com.educloud.user.service.PasswordChangeService.class))
                .changePassword(
                        org.mockito.ArgumentMatchers.eq(1001L),
                        org.mockito.ArgumentMatchers.eq("old-pass"),
                        org.mockito.ArgumentMatchers.eq("new-pass-123"),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void signingKeyStatusRequiresSecurityAuthority() throws Exception {
        mockMvc.perform(get("/api/v1/security/signing-key-status").with(jwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/security/signing-key-status")
                        .with(jwt().authorities(new SimpleGrantedAuthority("security:key-status:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeKid").value(org.hamcrest.Matchers.startsWith("educloud-user-")));
    }

    private static String writePkcs8Key() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) pair.getPrivate();
        String base64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + wrap(base64) + "-----END PRIVATE KEY-----\n";
        Path keyFile = Files.createTempFile("method-security-", ".pem");
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
    static class MockServices {

        @Bean
        ProfileService profileService() {
            return mock(ProfileService.class);
        }

        @Bean
        UserAdminService userAdminService() {
            return mock(UserAdminService.class);
        }

        @Bean
        UserStatusService userStatusService() {
            return mock(UserStatusService.class);
        }

        @Bean
        RoleService roleService() {
            return mock(RoleService.class);
        }

        @Bean
        PlatformConfigService platformConfigService() {
            return mock(PlatformConfigService.class);
        }

        @Bean
        SigningKeyStatusService signingKeyStatusService() {
            return mock(SigningKeyStatusService.class);
        }

        @Bean
        RegistrationService registrationService() {
            return mock(RegistrationService.class);
        }

        @Bean
        AuthenticationService authenticationService() {
            return mock(AuthenticationService.class);
        }

        @Bean
        RefreshSessionService refreshSessionService() {
            return mock(RefreshSessionService.class);
        }

        @Bean
        com.educloud.user.service.PasswordChangeService passwordChangeService() {
            return mock(com.educloud.user.service.PasswordChangeService.class);
        }

        @Bean
        IdempotencyService idempotencyService() {
            return mock(IdempotencyService.class);
        }

        @Bean
        SessionRevocationService sessionRevocationService() {
            return mock(SessionRevocationService.class);
        }

        @Bean
        com.educloud.user.mapper.SysPermissionMapper sysPermissionMapper() {
            return mock(com.educloud.user.mapper.SysPermissionMapper.class);
        }

        @Bean
        com.educloud.user.messaging.OutboxWriter outboxWriter() {
            return mock(com.educloud.user.messaging.OutboxWriter.class);
        }

        @Bean
        com.educloud.user.support.AuditWriter auditWriter() {
            return mock(com.educloud.user.support.AuditWriter.class);
        }

        @Bean
        org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate() {
            return mock(org.springframework.data.redis.core.StringRedisTemplate.class);
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
        com.educloud.user.mapper.SysUserMapper sysUserMapper() {
            return mock(com.educloud.user.mapper.SysUserMapper.class);
        }

        @Bean
        com.educloud.user.mapper.UserProfileMapper userProfileMapper() {
            return mock(com.educloud.user.mapper.UserProfileMapper.class);
        }

        @Bean
        com.educloud.user.mapper.SysRoleMapper sysRoleMapper() {
            return mock(com.educloud.user.mapper.SysRoleMapper.class);
        }

        @Bean
        com.educloud.user.mapper.SysUserRoleMapper sysUserRoleMapper() {
            return mock(com.educloud.user.mapper.SysUserRoleMapper.class);
        }

        @Bean
        com.educloud.user.mapper.RefreshSessionMapper refreshSessionMapper() {
            return mock(com.educloud.user.mapper.RefreshSessionMapper.class);
        }

        @Bean
        com.educloud.user.mapper.LoginAuditMapper loginAuditMapper() {
            return mock(com.educloud.user.mapper.LoginAuditMapper.class);
        }

        @Bean
        com.educloud.user.mapper.AuditEventMapper auditEventMapper() {
            return mock(com.educloud.user.mapper.AuditEventMapper.class);
        }

        @Bean
        com.educloud.user.mapper.OutboxEventMapper outboxEventMapper() {
            return mock(com.educloud.user.mapper.OutboxEventMapper.class);
        }

        @Bean
        com.educloud.user.mapper.OutboxSequenceMapper outboxSequenceMapper() {
            return mock(com.educloud.user.mapper.OutboxSequenceMapper.class);
        }

        @Bean
        com.educloud.user.mapper.IdempotencyRecordMapper idempotencyRecordMapper() {
            return mock(com.educloud.user.mapper.IdempotencyRecordMapper.class);
        }

        @Bean
        com.educloud.user.mapper.PlatformPublicConfigMapper platformPublicConfigMapper() {
            return mock(com.educloud.user.mapper.PlatformPublicConfigMapper.class);
        }

        @Bean
        com.educloud.user.mapper.ServiceClientMapper serviceClientMapper() {
            return mock(com.educloud.user.mapper.ServiceClientMapper.class);
        }

        @Bean
        com.educloud.user.mapper.ServiceClientCredentialMapper serviceClientCredentialMapper() {
            return mock(com.educloud.user.mapper.ServiceClientCredentialMapper.class);
        }

        @Bean
        com.educloud.user.mapper.SysRolePermissionMapper sysRolePermissionMapper() {
            return mock(com.educloud.user.mapper.SysRolePermissionMapper.class);
        }

        @Bean
        com.educloud.user.mapper.InboxEventMapper inboxEventMapper() {
            return mock(com.educloud.user.mapper.InboxEventMapper.class);
        }
    }
}
