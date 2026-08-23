package com.educloud.file.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.file.config.FileProperties;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.file.dto.request.CreateUploadSessionRequest;
import com.educloud.file.dto.response.UploadSessionResponse;
import com.educloud.file.entity.FileObjectEntity;
import com.educloud.file.exception.UploadNotVerifiedException;
import com.educloud.file.security.SecurityConfiguration;
import com.educloud.file.service.FileObjectService;
import com.educloud.file.service.UploadSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务 9：对外上传会话控制器测试（@PreAuthorize + ApiResponse 信封 + 服务委托）。
 *
 * <p>依据：M04 设计规格 6.1 节 —— POST /api/v1/file-upload-sessions 需 file:upload；
 * complete 成功返回 FileObjectResponse；对象缺失经 FileExceptionHandler 映射为
 * 409 UPLOAD_NOT_VERIFIED 信封。</p>
 */
@WebMvcTest(FileUploadSessionController.class)
@Import(SecurityConfiguration.class)
class FileUploadSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UploadSessionService uploadSessionService;

    @MockBean
    private FileObjectService fileObjectService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void createRejectsMissingToken() throws Exception {
        mockMvc.perform(post("/api/v1/file-upload-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @WithMockJwt(permissions = {"file:other"})
    void createRejectsMissingUploadPermission() throws Exception {
        mockMvc.perform(post("/api/v1/file-upload-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_ACCESS_DENIED"));
    }

    @Test
    @WithMockJwt(permissions = {"file:upload"})
    void createDelegatesToServiceWithJwtSubject() throws Exception {
        when(uploadSessionService.create(eq(1001L), any(CreateUploadSessionRequest.class)))
                .thenReturn(new UploadSessionResponse("9001", "https://minio.example/put", 300L));

        mockMvc.perform(post("/api/v1/file-upload-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\",\"expectedSizeBytes\":2048,\"originalName\":\"avatar.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.sessionId").value(9001))
                .andExpect(jsonPath("$.data.uploadUrl").value("https://minio.example/put"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300));

        verify(uploadSessionService).create(eq(1001L), argThat(request ->
                "image/png".equals(request.contentType())
                        && request.expectedSizeBytes() == 2048L
                        && "avatar.png".equals(request.originalName())));
    }

    @Test
    @WithMockJwt(subject = "not-a-number", permissions = {"file:upload"})
    void createRejectsNonNumericSubjectWith401() throws Exception {
        mockMvc.perform(post("/api/v1/file-upload-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(uploadSessionService, fileObjectService);
    }

    @Test
    @WithMockJwt(subject = "not-a-number", permissions = {"file:upload"})
    void completeRejectsNonNumericSubjectWith401() throws Exception {
        mockMvc.perform(post("/api/v1/file-upload-sessions/55/complete"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(fileObjectService);
    }

    @Test
    @WithMockJwt(permissions = {"file:upload"})
    void completeReturnsFileObjectResponse() throws Exception {
        FileObjectEntity object = new FileObjectEntity();
        object.setId(77L);
        object.setObjectKey("educloud-files/user-1001/20260822/uuid.png");
        object.setContentType("image/png");
        object.setSizeBytes(2048L);
        object.setSha256("abc123");
        when(fileObjectService.completeUpload(1001L, 55L)).thenReturn(object);

        mockMvc.perform(post("/api/v1/file-upload-sessions/55/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.fileId").value(77))
                .andExpect(jsonPath("$.data.objectKey").value("educloud-files/user-1001/20260822/uuid.png"))
                .andExpect(jsonPath("$.data.sizeBytes").value(2048))
                .andExpect(jsonPath("$.data.sha256").value("abc123"))
                .andExpect(jsonPath("$.data.contentType").value("image/png"));

        verify(fileObjectService).completeUpload(1001L, 55L);
    }

    @Test
    @WithMockJwt(permissions = {"file:upload"})
    void completeMapsUploadNotVerifiedTo409Envelope() throws Exception {
        when(fileObjectService.completeUpload(1001L, 55L))
                .thenThrow(new UploadNotVerifiedException("object missing"));

        mockMvc.perform(post("/api/v1/file-upload-sessions/55/complete"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPLOAD_NOT_VERIFIED"))
                .andExpect(jsonPath("$.message").value("Upload could not be verified"))
                .andExpect(jsonPath("$.data").isEmpty());
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
                            "http://127.0.0.1:9000", "test-access", "test-secret", "educloud-files-test"),
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