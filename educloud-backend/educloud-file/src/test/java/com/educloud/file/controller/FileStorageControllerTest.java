package com.educloud.file.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.file.config.FileProperties;
import com.educloud.file.observability.FileMetrics;
import com.educloud.file.security.SecurityConfiguration;
import com.educloud.file.storage.StorageGateway;
import com.educloud.file.support.FileAccessAuditWriter;
import com.educloud.file.support.StorageStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务 9：存储状态与探测控制器测试（权限 + 脱敏 + probe + 审计）。
 *
 * <p>依据：M04 设计规格 6.1/9 节 —— storage-status 只返回脱敏端点（不出现 host 与
 * accessKey/secretKey）；storage-tests 触发 probe 并写 STORAGE_TEST 审计；
 * 两个端点均需对应权限码，越权 403。</p>
 */
@WebMvcTest(controllers = FileStorageController.class)
@Import({SecurityConfiguration.class, StorageStatusService.class})
class FileStorageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StorageGateway storageGateway;

    @MockBean
    private FileAccessAuditWriter auditWriter;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private FileMetrics metrics;

    @Test
    void statusRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/files/storage-status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @WithMockJwt(permissions = {"file:storage:test"})
    void statusRejectsMissingStatusReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/files/storage-status"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_ACCESS_DENIED"));
    }

    @Test
    @WithMockJwt(permissions = {"file:storage:status:read"})
    void statusMasksEndpointAndSecrets() throws Exception {
        when(storageGateway.probe())
                .thenReturn(new StorageGateway.StorageProbeResult(true, null));

        mockMvc.perform(get("/api/v1/files/storage-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("MINIO"))
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.endpointMasked").value("http://***:9000"))
                .andExpect(jsonPath("$.data.checkedAt").exists())
                .andExpect(jsonPath("$.data.lastErrorCategory").value(nullValue()))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("super-secret-access"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("super-secret-secret"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("127.0.0.1"))));
    }

    @Test
    @WithMockJwt(permissions = {"file:storage:status:read"})
    void statusReportsFailureCategory() throws Exception {
        when(storageGateway.probe())
                .thenReturn(new StorageGateway.StorageProbeResult(false, "CONNECTION"));

        mockMvc.perform(get("/api/v1/files/storage-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(false))
                .andExpect(jsonPath("$.data.endpointMasked").value("http://***:9000"))
                .andExpect(jsonPath("$.data.lastErrorCategory").value("CONNECTION"));
    }

    @Test
    @WithMockJwt(permissions = {"file:storage:status:read"})
    void storageTestRejectsMissingTestPermission() throws Exception {
        mockMvc.perform(post("/api/v1/files/storage-tests"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_ACCESS_DENIED"));
    }

    @Test
    @WithMockJwt(subject = "not-a-number", permissions = {"file:storage:test"})
    void storageTestRejectsNonNumericSubjectWith401() throws Exception {
        mockMvc.perform(post("/api/v1/files/storage-tests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(storageGateway, auditWriter);
    }

    @Test
    @WithMockJwt(permissions = {"file:storage:test"})
    void storageTestRunsProbeAndWritesSuccessAudit() throws Exception {
        when(storageGateway.probe())
                .thenReturn(new StorageGateway.StorageProbeResult(true, null));

        mockMvc.perform(post("/api/v1/files/storage-tests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.latencyMs").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.errorCategory").value(nullValue()));

        verify(auditWriter).write(
                eq(0L), eq(1001L), eq(FileAccessAuditWriter.ACTION_STORAGE_TEST),
                eq(FileAccessAuditWriter.RESULT_SUCCESS));
        verify(metrics).recordStorageTest();
    }

    @Test
    @WithMockJwt(permissions = {"file:storage:test"})
    void storageTestWritesFailureAuditOnProbeFailure() throws Exception {
        when(storageGateway.probe())
                .thenReturn(new StorageGateway.StorageProbeResult(false, "IO"));

        mockMvc.perform(post("/api/v1/files/storage-tests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(false))
                .andExpect(jsonPath("$.data.errorCategory").value("IO"));

        verify(auditWriter).write(
                eq(0L), eq(1001L), eq(FileAccessAuditWriter.ACTION_STORAGE_TEST),
                eq(FileAccessAuditWriter.RESULT_FAILURE));
        verify(metrics).recordStorageTest();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestInfrastructure {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-22T10:00:00Z"), ZoneOffset.UTC);
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
                            "http://127.0.0.1:9000", "super-secret-access", "super-secret-secret", "educloud-files-test"),
                    new FileProperties.Upload(
                            10485760,
                            List.of("image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf"),
                            Duration.ofMinutes(5),
                            Duration.ofMinutes(15)),
                    new FileProperties.DownloadGrant(
                            Duration.ofMinutes(5), Duration.ofMinutes(15), List.of("PROFILE_AVATAR", "PUBLIC_CATALOG")),
                    new FileProperties.Cleanup(Duration.ofHours(24), Duration.ofMinutes(15), 50),
                    new FileProperties.StorageTest(1, Duration.ofMinutes(1)),
                    new FileProperties.Internal("bootstrap-key", List.of("user-service"), "educloud-file"),
                    new FileProperties.Jwt("file:/jwks.json", "https://issuer.educloud.local", "educloud-api"),
                    "local");
        }
    }
}