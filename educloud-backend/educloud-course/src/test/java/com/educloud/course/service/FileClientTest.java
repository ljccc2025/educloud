package com.educloud.course.service;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.course.config.CourseFileProperties;
import com.educloud.course.exception.CourseErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * FileClient 单元测试（M05 任务 12）：复刻 user 版 FileClientTest —— 服务令牌缓存提前
 * 30s、bind 携带委托上传者（uploaderUserId）、403→COURSE_ACCESS_DENIED、404→COURSE_NOT_FOUND、
 * 其余非 2xx→DEPENDENCY_UNAVAILABLE、批量 grant（ANONYMOUS/USER + PUBLIC_CATALOG，
 * ≤100/次）、enabled=false no-op。
 */
@ExtendWith(MockitoExtension.class)
class FileClientTest {

    private static final String ENDPOINT = "http://127.0.0.1:8087";

    @Mock
    private ServiceTokenService serviceTokenService;

    private CourseFileProperties properties;
    private MockRestServiceServer server;
    private FileClient client;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        properties = new CourseFileProperties(
                ENDPOINT, "educloud-course", "s3cret", true, Duration.ofSeconds(3),
                "http://127.0.0.1:8082");
        clock = new MutableClock(Instant.parse("2026-08-22T10:00:00Z"));
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.baseUrl(ENDPOINT).build();
        client = new FileClient(properties, serviceTokenService, restClient, clock);
    }

    private void stubToken(String token, long expiresInSeconds) {
        when(serviceTokenService.issue(
                eq("educloud-course"), eq("s3cret"), eq("educloud-file"),
                eq(List.of("file:internal")), isNull(), isNull()))
                .thenReturn(new ServiceTokenService.IssueResult(token, expiresInSeconds));
    }

    @Test
    void bindCoverSendsBearerTokenOwnerAndDelegateUploader() {
        stubToken("tok-1", 300L);
        server.expect(requestTo(ENDPOINT + "/internal/v1/files/9001/bind"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ownerType").value("COURSE"))
                .andExpect(jsonPath("$.ownerId").value("101"))
                .andExpect(jsonPath("$.uploaderUserId").value(1001))
                .andRespond(withSuccess("{\"status\":\"BOUND\"}", MediaType.APPLICATION_JSON));

        client.bindCover(101L, 9001L, 1001L);

        server.verify();
    }

    @Test
    void bindForbiddenMapsToCourseAccessDenied() {
        stubToken("tok-1", 300L);
        server.expect(requestTo(ENDPOINT + "/internal/v1/files/9001/bind"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.bindCover(101L, 9001L, 1001L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(CourseErrorCode.COURSE_ACCESS_DENIED);

        server.verify();
    }

    @Test
    void bindNotFoundMapsToCourseNotFound() {
        stubToken("tok-1", 300L);
        server.expect(requestTo(ENDPOINT + "/internal/v1/files/9001/bind"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.bindCover(101L, 9001L, 1001L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(CourseErrorCode.COURSE_NOT_FOUND);

        server.verify();
    }

    @Test
    void bindServiceUnavailableMapsToDependencyUnavailable() {
        stubToken("tok-1", 300L);
        server.expect(requestTo(ENDPOINT + "/internal/v1/files/9001/bind"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.bindCover(101L, 9001L, 1001L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(CommonErrorCode.DEPENDENCY_UNAVAILABLE);

        server.verify();
    }

    @Test
    void unbindCoverSendsOwnerBody() {
        stubToken("tok-1", 300L);
        server.expect(requestTo(ENDPOINT + "/internal/v1/files/9001/unbind"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andExpect(jsonPath("$.ownerType").value("COURSE"))
                .andExpect(jsonPath("$.ownerId").value("101"))
                .andRespond(withSuccess("{\"status\":\"UNBOUND\"}", MediaType.APPLICATION_JSON));

        client.unbindCover(101L, 9001L);

        server.verify();
    }

    @Test
    void grantPublicCatalogSendsAnonymousBatchAndParsesOnlyGrantedItems() {
        stubToken("tok-1", 300L);
        server.expect(requestTo(ENDPOINT + "/internal/v1/file-download-grants/batch"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andExpect(jsonPath("$.subjectType").value("ANONYMOUS"))
                .andExpect(jsonPath("$.purpose").value("PUBLIC_CATALOG"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].requestKey").value("9001"))
                .andExpect(jsonPath("$.items[0].ownerType").value("COURSE"))
                .andExpect(jsonPath("$.items[0].ownerId").value("101"))
                .andRespond(withSuccess(
                        "{\"items\":["
                                + "{\"requestKey\":\"9001\",\"fileId\":9001,\"status\":\"GRANTED\","
                                + "\"url\":\"http://bucket/course-cover-9001\",\"expiresAt\":\"2026-08-22T10:05:00Z\"},"
                                + "{\"requestKey\":\"9002\",\"fileId\":9002,\"status\":\"UNAVAILABLE\","
                                + "\"url\":null,\"expiresAt\":null}"
                                + "]}",
                        MediaType.APPLICATION_JSON));

        // LinkedHashMap 保证 items 顺序与断言一致（Map.of 迭代顺序未定义）。
        java.util.LinkedHashMap<Long, Long> ownerCourseIdByFileId = new java.util.LinkedHashMap<>();
        ownerCourseIdByFileId.put(9001L, 101L);
        ownerCourseIdByFileId.put(9002L, 102L);
        Map<Long, String> urls = client.grantPublicCatalogUrls(ownerCourseIdByFileId);

        assertThat(urls).containsOnlyKeys(9001L);
        assertThat(urls.get(9001L)).isEqualTo("http://bucket/course-cover-9001");
        server.verify();
    }

    @Test
    void grantCatalogWithUserSubjectCarriesSubjectUserId() {
        stubToken("tok-1", 300L);
        server.expect(requestTo(ENDPOINT + "/internal/v1/file-download-grants/batch"))
                .andExpect(jsonPath("$.subjectType").value("USER"))
                .andExpect(jsonPath("$.subjectUserId").value(1001L))
                .andExpect(jsonPath("$.purpose").value("PUBLIC_CATALOG"))
                .andExpect(jsonPath("$.items[0].ownerType").value("COURSE"))
                .andExpect(jsonPath("$.items[0].ownerId").value("101"))
                .andRespond(withSuccess(
                        "{\"items\":[{\"requestKey\":\"9001\",\"fileId\":9001,\"status\":\"GRANTED\","
                                + "\"url\":\"http://bucket/course-cover\",\"expiresAt\":\"2026-08-22T10:05:00Z\"}]}",
                        MediaType.APPLICATION_JSON));

        Map<Long, String> urls = client.grantCatalogUrls(Map.of(9001L, 101L), 1001L);

        assertThat(urls.get(9001L)).isEqualTo("http://bucket/course-cover");
        server.verify();
    }

    @Test
    void tokenIsCachedUntilThirtySecondsBeforeExpiry() {
        stubToken("tok-1", 300L);
        server.expect(requestTo(ENDPOINT + "/internal/v1/files/9001/bind"))
                .andRespond(withSuccess("{\"status\":\"BOUND\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ENDPOINT + "/internal/v1/files/9001/bind"))
                .andRespond(withSuccess("{\"status\":\"BOUND\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ENDPOINT + "/internal/v1/files/9002/bind"))
                .andRespond(withSuccess("{\"status\":\"BOUND\"}", MediaType.APPLICATION_JSON));

        client.bindCover(101L, 9001L, 1001L);
        client.bindCover(101L, 9001L, 1001L);
        verify(serviceTokenService, times(1)).issue(any(), any(), any(), any(), any(), any());

        clock.advance(Duration.ofSeconds(271L));
        client.bindCover(101L, 9002L, 1001L);
        verify(serviceTokenService, times(2)).issue(any(), any(), any(), any(), any(), any());
        server.verify();
    }

    @Test
    void disabledClientIsNoOp() {
        FileClient disabled = new FileClient(
                new CourseFileProperties(ENDPOINT, "educloud-course", "s3cret", false,
                        Duration.ofSeconds(3), "http://127.0.0.1:8082"),
                serviceTokenService,
                RestClient.builder().baseUrl(ENDPOINT).build(),
                clock);

        disabled.bindCover(101L, 9001L, 1001L);
        disabled.unbindCover(101L, 9001L);

        assertThat(disabled.grantPublicCatalogUrls(Map.of(9001L, 101L))).isEmpty();
        assertThat(disabled.grantCatalogUrls(Map.of(9001L, 101L), 1001L)).isEmpty();
        verify(serviceTokenService, never()).issue(any(), any(), any(), any(), any(), any());
        server.verify();
    }

    /** 可推进的测试时钟。 */
    static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
