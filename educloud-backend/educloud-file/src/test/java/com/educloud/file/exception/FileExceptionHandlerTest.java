package com.educloud.file.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.BusinessException;
import com.educloud.common.web.RequestContext;
import com.educloud.common.web.RequestContextFilter;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.file.storage.FileStorageException;
import com.educloud.file.storage.FileTooLargeException;
import com.educloud.file.storage.FileTypeNotAllowedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务 7：FileErrorCode + FileExceptionHandler 的 MockMvc standalone 契约测试。
 *
 * <p>断言各内部异常 → 对外错误码的 HTTP 状态、响应体 code/requestId/data，
 * 以及 500 兜底不泄漏内部路径/堆栈。storage 包子类（FileTooLargeException、
 * FileTypeNotAllowedException）必须命中自身错误码而非父类 FileStorageException。</p>
 */
class FileExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var requestIdPolicy = new RequestIdPolicy(UUID::randomUUID);
        var requestContext = new ServletRequestContextAccessor(requestIdPolicy, null);
        var responses = new ApiResponseFactory(
                requestContext,
                Clock.fixed(Instant.parse("2026-08-22T08:00:00Z"), ZoneOffset.UTC));
        mockMvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new FileExceptionHandler(responses))
                .addFilters(new RequestContextFilter(requestIdPolicy))
                .build();
    }

    @ParameterizedTest
    @MethodSource("faultMappings")
    void mapsInternalExceptionsToStableErrorResponses(
            String fault,
            int expectedStatus,
            String expectedCode,
            String expectedMessage) throws Exception {
        mockMvc.perform(get("/fault/{fault}", fault)
                        .header(RequestContext.REQUEST_ID_HEADER, "req-file"))
                .andExpect(status().is(expectedStatus))
                .andExpect(header().string(RequestContext.REQUEST_ID_HEADER, "req-file"))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.requestId").value("req-file"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void mapsStorageSubclassesToTheirOwnCodesNotTheStorageParent() throws Exception {
        mockMvc.perform(get("/fault/file-type-not-allowed"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));
        mockMvc.perform(get("/fault/file-too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void mapsBusinessErrorCodeToUnifiedResponse() throws Exception {
        mockMvc.perform(get("/business/STORAGE_TEST_RATE_LIMITED")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-rate"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("STORAGE_TEST_RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value("Storage test rate limit exceeded"))
                .andExpect(jsonPath("$.requestId").value("req-rate"));
    }

    @Test
    void mapsSpringSecurityAccessDeniedToFileAccessDenied() throws Exception {
        mockMvc.perform(get("/denied")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_ACCESS_DENIED"))
                .andExpect(jsonPath("$.requestId").value("req-denied"));
    }

    @Test
    void mapsBeanValidationToStable400Response() throws Exception {
        mockMvc.perform(post("/validation")
                        .header(RequestContext.REQUEST_ID_HEADER, "req-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestContext.REQUEST_ID_HEADER, "req-validation"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.requestId").value("req-validation"))
                .andExpect(jsonPath("$.data.violations[0].field").value("name"));
    }

    @Test
    void doesNotLeakInternalExceptionMessageOrStackTrace() throws Exception {
        String body = mockMvc.perform(get("/fault/upload-session-expired"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("UPLOAD_SESSION_EXPIRED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain(
                "/srv/secret-uploads",
                "UploadSessionExpiredException",
                "at com.educloud",
                "stackTrace");
    }

    @Test
    void mapsAnUnknownExceptionToOneSafe500AndLogsItOnce() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(FileExceptionHandler.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            String body = mockMvc.perform(get("/unexpected")
                            .header(RequestContext.REQUEST_ID_HEADER, "req-500"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(header().string(RequestContext.REQUEST_ID_HEADER, "req-500"))
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Internal server error"))
                    .andExpect(jsonPath("$.requestId").value("req-500"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(body).doesNotContain(
                    "jdbc:mysql://db.internal",
                    "IllegalStateException",
                    "SQL",
                    "stackTrace");
            assertThat(appender.list.stream().filter(event -> event.getLevel() == Level.ERROR))
                    .hasSize(1);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static Stream<Arguments> faultMappings() {
        return Stream.of(
                Arguments.of("upload-session-expired", 410, "UPLOAD_SESSION_EXPIRED",
                        "Upload session has expired"),
                Arguments.of("upload-session-not-found", 404, "UPLOAD_SESSION_NOT_FOUND",
                        "Upload session not found"),
                Arguments.of("upload-not-verified", 409, "UPLOAD_NOT_VERIFIED",
                        "Upload could not be verified"),
                Arguments.of("upload-session-state", 409, "UPLOAD_NOT_VERIFIED",
                        "Upload could not be verified"),
                Arguments.of("upload-session-access-denied", 403, "FILE_ACCESS_DENIED",
                        "File access denied"),
                Arguments.of("file-type-not-allowed", 415, "FILE_TYPE_NOT_ALLOWED",
                        "File type is not allowed"),
                Arguments.of("file-too-large", 413, "FILE_TOO_LARGE",
                        "File is too large"),
                Arguments.of("file-storage", 503, "DEPENDENCY_UNAVAILABLE",
                        "Dependency unavailable"),
                Arguments.of("file-not-found", 404, "FILE_NOT_FOUND",
                        "File not found"),
                Arguments.of("file-not-available", 404, "FILE_NOT_FOUND",
                        "File not found"),
                Arguments.of("file-bound", 409, "FILE_BOUND",
                        "File has active bindings"),
                Arguments.of("file-access-denied", 403, "FILE_ACCESS_DENIED",
                        "File access denied"),
                Arguments.of("grant-purpose-not-allowed", 403, "GRANT_PURPOSE_NOT_ALLOWED",
                        "Grant purpose is not allowed"),
                Arguments.of("version-conflict", 409, "VERSION_CONFLICT",
                        "Resource version conflict"));
    }

    @RestController
    private static final class FailureController {

        @GetMapping("/fault/{fault}")
        void fault(@PathVariable String fault) {
            switch (fault) {
                case "upload-session-expired" -> throw new UploadSessionExpiredException(
                        "internal session path /srv/secret-uploads");
                case "upload-session-not-found" -> throw new UploadSessionNotFoundException(
                        "internal: session missing");
                case "upload-not-verified" -> throw new UploadNotVerifiedException(
                        "internal: object missing");
                case "upload-session-state" -> throw new UploadSessionStateException(
                        "internal: session already completed");
                case "upload-session-access-denied" -> throw new UploadSessionAccessDeniedException(
                        "internal: uploader mismatch");
                case "file-type-not-allowed" -> throw new FileTypeNotAllowedException(
                        "internal: content-type rejected");
                case "file-too-large" -> throw new FileTooLargeException(
                        "internal: size exceeded");
                case "file-storage" -> throw new FileStorageException(
                        "internal: minio down");
                case "file-not-found" -> throw new FileNotFoundException(
                        "internal: root row missing");
                case "file-not-available" -> throw new FileNotAvailableException(
                        "internal: status is UPLOADING");
                case "file-bound" -> throw new FileBoundException(
                        "internal: active binding exists");
                case "file-access-denied" -> throw new FileAccessDeniedException(
                        "internal: owner mismatch");
                case "grant-purpose-not-allowed" -> throw new GrantPurposeNotAllowedException(
                        "internal: purpose not allowed");
                case "version-conflict" -> throw new VersionConflictException(
                        "internal: version mismatch");
                default -> throw new IllegalArgumentException("unknown fault " + fault);
            }
        }

        @GetMapping("/business/{errorCode}")
        void business(@PathVariable FileErrorCode errorCode) {
            throw new BusinessException(errorCode);
        }

        @GetMapping("/denied")
        void denied() {
            throw new AccessDeniedException("no");
        }

        @PostMapping("/validation")
        void validate(@Valid @RequestBody Input input) {}

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("jdbc:mysql://db.internal SQL secret");
        }
    }

    private record Input(@NotBlank String name) {}
}
