package com.educloud.file.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.file.config.FileProperties;
import com.educloud.file.entity.FileBindingEntity;
import com.educloud.file.entity.FileObjectEntity;
import com.educloud.file.mapper.FileBindingMapper;
import com.educloud.file.mapper.FileObjectMapper;
import com.educloud.file.messaging.FileEventPublisher;
import com.educloud.file.observability.FileMetrics;
import com.educloud.file.security.SecurityConfiguration;
import com.educloud.file.service.DownloadGrantService;
import com.educloud.file.service.FileBindingService;
import com.educloud.file.service.FileObjectService;
import com.educloud.file.service.UploadSessionService;
import com.educloud.file.storage.StorageGateway;
import com.educloud.file.support.FileAccessAuditWriter;
import com.educloud.file.support.GrantPurposePolicy;
import com.educloud.file.support.OwnerServiceRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务 10：内部文件 API 控制器测试（服务令牌经 InternalApiFilter 模拟 CLIENT_ID attribute）。
 *
 * <p>依据：M04 设计规格 6.2/9 节 —— 内部接口不经过 @PreAuthorize，ownerService 恒由已认证
 * clientId 推导（user-service→user）；未知 clientId 403；bind/unbind/delete 委托
 * FileBindingService/FileObjectService；availability 需调用方有活跃绑定；download-grants
 * 单文件与 batch 走 DownloadGrantService，批量伪造 owner 整批 403 + GRANT_BATCH_DENIED 审计。
 * FileObjectService/DownloadGrantService 用真实 bean（依赖全部 mock），让删除的
 * “曾绑定”校验与批量伪造审计路径在控制器切片内真实执行。</p>
 */
@WebMvcTest(InternalFileController.class)
@Import({
        SecurityConfiguration.class,
        OwnerServiceRegistry.class,
        FileObjectService.class,
        DownloadGrantService.class
})
class InternalFileControllerTest {

    private static final long FILE_ID = 1001L;
    private static final String BUCKET = "educloud-files";
    private static final String OBJECT_KEY = "educloud-files/user-42/20260822/abc.png";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileBindingService bindingService;

    @MockBean
    private UploadSessionService uploadSessionService;

    @MockBean
    private FileObjectMapper objectMapper;

    @MockBean
    private FileBindingMapper bindingMapper;

    @MockBean
    private StorageGateway storageGateway;

    @MockBean
    private FileEventPublisher fileEventPublisher;

    @MockBean
    private FileAccessAuditWriter auditWriter;

    @MockBean
    private com.educloud.file.observability.FileMetrics metrics;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsMissingClientIdAttribute() throws Exception {
        // InternalApiFilter 在 Security 链之前拦截：无服务令牌 → 401（任务允许 403/401）。
        mockMvc.perform(get("/internal/v1/files/1001/availability"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsClientIdNotRegisteredInOwnerServiceRegistry() throws Exception {
        when(jwtDecoder.decode("evil-token")).thenReturn(serviceToken("evil-service"));

        mockMvc.perform(get("/internal/v1/files/1001/availability")
                        .header("Authorization", "Bearer evil-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_ACCESS_DENIED"));

        verifyNoInteractions(bindingService, bindingMapper, objectMapper);
    }

    @Test
    void bindDelegatesWithOwnerServiceDerivedFromClientId() throws Exception {
        when(jwtDecoder.decode("user-token")).thenReturn(serviceToken("user-service"));

        mockMvc.perform(post("/internal/v1/files/1001/bind")
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerType\":\"USER_PROFILE\",\"ownerId\":\"u-42\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOUND"));

        verify(bindingService).bind(1001L, "user", "USER_PROFILE", "u-42");
    }

    @Test
    void bindRejectsOverlongOwnerFieldsWith400() throws Exception {
        when(jwtDecoder.decode("user-token")).thenReturn(serviceToken("user-service"));
        String longOwnerId = "u-" + "x".repeat(130);

        mockMvc.perform(post("/internal/v1/files/1001/bind")
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerType\":\"USER_PROFILE\",\"ownerId\":\"" + longOwnerId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(bindingService);
    }

    @Test
    void unbindDelegatesWithOwnerServiceDerivedFromClientId() throws Exception {
        when(jwtDecoder.decode("user-token")).thenReturn(serviceToken("user-service"));

        mockMvc.perform(post("/internal/v1/files/1001/unbind")
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerType\":\"USER_PROFILE\",\"ownerId\":\"u-42\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNBOUND"));

        verify(bindingService).unbind(1001L, "user", "USER_PROFILE", "u-42");
    }

    @Test
    void availabilityReturnsFileStateWhenCallerHasBinding() throws Exception {
        when(jwtDecoder.decode("user-token")).thenReturn(serviceToken("user-service"));
        when(bindingService.hasActiveBindingByOwnerService(FILE_ID, "user")).thenReturn(true);
        when(objectMapper.selectById(FILE_ID)).thenReturn(availableFile());

        mockMvc.perform(get("/internal/v1/files/1001/availability")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.sizeBytes").value(2048));

        verify(bindingService).hasActiveBindingByOwnerService(FILE_ID, "user");
        verify(objectMapper).selectById(FILE_ID);
    }

    @Test
    void availabilityReportsMissingWhenBoundButObjectDeleted() throws Exception {
        when(jwtDecoder.decode("user-token")).thenReturn(serviceToken("user-service"));
        when(bindingService.hasActiveBindingByOwnerService(FILE_ID, "user")).thenReturn(true);
        when(objectMapper.selectById(FILE_ID)).thenReturn(null);

        mockMvc.perform(get("/internal/v1/files/1001/availability")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false))
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    void availabilityRejectsWhenCallerHasNoBinding() throws Exception {
        when(jwtDecoder.decode("user-token")).thenReturn(serviceToken("user-service"));
        when(bindingService.hasActiveBindingByOwnerService(FILE_ID, "user")).thenReturn(false);

        mockMvc.perform(get("/internal/v1/files/1001/availability")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_ACCESS_DENIED"));

        verify(objectMapper, never()).selectById(anyLong());
    }

    @Test
    void downloadGrantDelegatesWithOwnerServiceAndTtlMapping() throws Exception {
        when(jwtDecoder.decode("user-token")).thenReturn(serviceToken("user-service"));
        when(bindingMapper.findActiveByOwner(FILE_ID, "user", "USER_PROFILE", "u-42"))
                .thenReturn(activeBinding("u-42"));
        when(objectMapper.selectById(FILE_ID)).thenReturn(availableFile());
        when(storageGateway.presignedGetUrl(BUCKET, OBJECT_KEY, Duration.ofSeconds(120)))
                .thenReturn("https://minio.example/educloud-files/abc.png?X-Amz-Signature=s1");

        mockMvc.perform(post("/internal/v1/files/1001/download-grants")
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectType":"USER","subjectUserId":42,"ownerType":"USER_PROFILE","ownerId":"u-42","purpose":"PROFILE_AVATAR","requestedTtlSeconds":120}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GRANTED"))
                .andExpect(jsonPath("$.url").value("https://minio.example/educloud-files/abc.png?X-Amz-Signature=s1"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        verify(bindingMapper).findActiveByOwner(FILE_ID, "user", "USER_PROFILE", "u-42");
        verify(storageGateway).presignedGetUrl(BUCKET, OBJECT_KEY, Duration.ofSeconds(120));
        verify(auditWriter).writeGrantSingle(FILE_ID, 42L, true);
    }

    @Test
    void batchGrantDelegatesAndReturnsPerItemResults() throws Exception {
        when(jwtDecoder.decode("user-token")).thenReturn(serviceToken("user-service"));
        when(bindingMapper.findActiveByOwner(FILE_ID, "user", "USER_PROFILE", "u-42"))
                .thenReturn(activeBinding("u-42"));
        when(objectMapper.selectById(FILE_ID)).thenReturn(availableFile());
        when(storageGateway.presignedGetUrl(BUCKET, OBJECT_KEY, Duration.ofMinutes(5)))
                .thenReturn("https://minio.example/educloud-files/abc.png?X-Amz-Signature=s1");

        mockMvc.perform(post("/internal/v1/file-download-grants/batch")
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectType":"USER","subjectUserId":42,"purpose":"PROFILE_AVATAR","items":[{"requestKey":"k1","fileId":1001,"ownerType":"USER_PROFILE","ownerId":"u-42"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].requestKey").value("k1"))
                .andExpect(jsonPath("$.items[0].fileId").value(1001))
                .andExpect(jsonPath("$.items[0].status").value("GRANTED"));

        verify(bindingMapper).findActiveByOwner(FILE_ID, "user", "USER_PROFILE", "u-42");
    }

    @Test
    void batchGrantRejectsForgedOwnerWith403AndAudit() throws Exception {
        when(jwtDecoder.decode("user-token")).thenReturn(serviceToken("user-service"));
        when(bindingMapper.findActiveByOwner(FILE_ID, "user", "USER_PROFILE", "u-42"))
                .thenReturn(activeBinding("u-42"));
        when(objectMapper.selectById(FILE_ID)).thenReturn(availableFile());
        when(storageGateway.presignedGetUrl(BUCKET, OBJECT_KEY, Duration.ofMinutes(5)))
                .thenReturn("https://minio.example/educloud-files/abc.png?X-Amz-Signature=s1");
        // 伪造项：同 (service,type) 但 ownerId 不匹配 → 视为伪造，整批 403
        when(bindingMapper.findActiveByOwner(FILE_ID, "user", "USER_PROFILE", "u-999"))
                .thenReturn(null);
        when(bindingMapper.findActiveByFileId(FILE_ID)).thenReturn(List.of(activeBinding("u-42")));

        mockMvc.perform(post("/internal/v1/file-download-grants/batch")
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectType":"USER","subjectUserId":42,"purpose":"PROFILE_AVATAR","items":[{"requestKey":"k-good","fileId":1001,"ownerType":"USER_PROFILE","ownerId":"u-42"},{"requestKey":"k-forged","fileId":1001,"ownerType":"USER_PROFILE","ownerId":"u-999"}]}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_ACCESS_DENIED"));

        verify(auditWriter).writeGrantBatchDenied(FILE_ID, 42L);
    }

    @Test
    void deleteDelegatesWhenOwnerHadBinding() throws Exception {
        when(jwtDecoder.decode("user-token")).thenReturn(serviceToken("user-service"));
        when(bindingMapper.findByOwner(FILE_ID, "user", "USER_PROFILE", "u-42"))
                .thenReturn(historicalBinding());
        when(objectMapper.selectByIdForUpdate(FILE_ID)).thenReturn(availableFile());
        when(bindingMapper.countActiveByFileId(FILE_ID)).thenReturn(0L);
        when(objectMapper.updateById(any(FileObjectEntity.class))).thenReturn(1);

        mockMvc.perform(post("/internal/v1/files/1001/delete")
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerType\":\"USER_PROFILE\",\"ownerId\":\"u-42\",\"reason\":\"user-removed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETED"));

        verify(bindingMapper).findByOwner(FILE_ID, "user", "USER_PROFILE", "u-42");
        verify(storageGateway).deleteObject(BUCKET, OBJECT_KEY);
    }

    @Test
    void deleteRejectsWhenOwnerNeverBound() throws Exception {
        when(jwtDecoder.decode("user-token")).thenReturn(serviceToken("user-service"));
        when(objectMapper.selectByIdForUpdate(FILE_ID)).thenReturn(availableFile());
        when(bindingMapper.findByOwner(FILE_ID, "user", "USER_PROFILE", "u-42"))
                .thenReturn(null);

        mockMvc.perform(post("/internal/v1/files/1001/delete")
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerType\":\"USER_PROFILE\",\"ownerId\":\"u-42\",\"reason\":\"user-removed\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_ACCESS_DENIED"));

        verify(storageGateway, never()).deleteObject(anyString(), anyString());
    }

    private Jwt serviceToken(String clientId) {
        Instant now = Instant.now();
        return new Jwt(
                "svc-token",
                now.minusSeconds(60),
                now.plusSeconds(300),
                Map.of("alg", "none"),
                Map.of("clientId", clientId, "aud", List.of("educloud-file")));
    }

    private FileBindingEntity activeBinding(String ownerId) {
        FileBindingEntity binding = new FileBindingEntity();
        binding.setId(9L);
        binding.setFileId(FILE_ID);
        binding.setOwnerService("user");
        binding.setOwnerType("USER_PROFILE");
        binding.setOwnerId(ownerId);
        binding.setBoundAt(Instant.parse("2026-08-22T10:00:00Z"));
        binding.setUnboundAt(null);
        return binding;
    }

    private FileBindingEntity historicalBinding() {
        FileBindingEntity binding = activeBinding("u-42");
        binding.setUnboundAt(Instant.parse("2026-08-22T11:00:00Z"));
        return binding;
    }

    private FileObjectEntity availableFile() {
        FileObjectEntity file = new FileObjectEntity();
        file.setId(FILE_ID);
        file.setObjectKey(OBJECT_KEY);
        file.setBucket(BUCKET);
        file.setContentType("image/png");
        file.setSizeBytes(2048L);
        file.setStatus("AVAILABLE");
        file.setVersion(1);
        return file;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestInfrastructure {

        @Bean
        MockMvcBuilderCustomizer internalServletPathCustomizer() {
            // 真实容器中 servletPath 与内部路由对齐；MockMvc 请求默认 servletPath 为空，
            // 而 InternalApiFilter.shouldNotFilter 按 getServletPath 判断，这里补齐映射。
            return builder -> builder.defaultRequest(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/")
                    .with(request -> {
                        if (request.getRequestURI().startsWith("/internal/v1/")) {
                            request.setServletPath(request.getRequestURI());
                        }
                        return request;
                    }));
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-22T11:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        RequestIdPolicy requestIdPolicy() {
            return new RequestIdPolicy(UUID::randomUUID);
        }

        @Bean
        RequestContextAccessor requestContextAccessor(RequestIdPolicy requestIdPolicy) {
            return new ServletRequestContextAccessor(requestIdPolicy, null);
        }

        @Bean
        ApiResponseFactory apiResponseFactory(
                RequestContextAccessor requestContextAccessor, Clock clock) {
            return new ApiResponseFactory(requestContextAccessor, clock);
        }

        @Bean
        FileProperties fileProperties() {
            return new FileProperties(
                    new FileProperties.Storage(
                            "http://127.0.0.1:9000", "test-access", "test-secret", BUCKET),
                    new FileProperties.Upload(
                            10485760,
                            List.of("image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf"),
                            Duration.ofMinutes(5),
                            Duration.ofMinutes(15)),
                    new FileProperties.DownloadGrant(
                            Duration.ofMinutes(5), Duration.ofMinutes(15),
                            List.of("PROFILE_AVATAR", "PUBLIC_CATALOG")),
                    new FileProperties.Cleanup(Duration.ofHours(24), Duration.ofMinutes(15), 50),
                    new FileProperties.StorageTest(1, Duration.ofMinutes(1)),
                    new FileProperties.Internal(
                            "bootstrap-key", List.of("user-service", "evil-service"), "educloud-file"),
                    new FileProperties.Jwt(
                            "file:/jwks.json", "https://issuer.educloud.local", "educloud-api"),
                    "local");
        }

        @Bean
        GrantPurposePolicy grantPurposePolicy() {
            return new GrantPurposePolicy(List.of("PROFILE_AVATAR", "PUBLIC_CATALOG"));
        }
    }
}
