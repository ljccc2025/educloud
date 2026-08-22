package com.educloud.user.support;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.user.config.FileClientProperties;
import com.educloud.user.service.ServiceTokenService;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anything;
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
 * FileClient 单元测试。依据：M04 计划任务 14（服务令牌缓存、bind/unbind、批量 grant
 * 解析 GRANTED 项、非 2xx 抛 DEPENDENCY_UNAVAILABLE、enabled=false no-op）。
 */
@ExtendWith(MockitoExtension.class)
class FileClientTest {

    private static final String ENDPOINT = "http://127.0.0.1:8087";

    @Mock
    private ServiceTokenService serviceTokenService;

    private FileClientProperties properties;
    private MockRestServiceServer server;
    private FileClient client;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        properties = new FileClientProperties(
                ENDPOINT, "user-service", "s3cret", true, Duration.ofSeconds(3));
        clock = new MutableClock(Instant.parse("2026-08-22T10:00:00Z"));
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.baseUrl(ENDPOINT).build();
        client = new FileClient(properties, serviceTokenService, restClient, clock);
    }

    private void stubToken(String token, long expiresInSeconds) {
        when(serviceTokenService.issue(
                eq("user-service"), eq("s3cret"), eq("educloud-file"),
                eq(List.of("file:internal")), isNull(), isNull()))
                .thenReturn(new ServiceTokenService.IssueResult(token, expiresInSeconds));
    }

    @Test
    void bindSendsBearerTokenAndOwnerBody() {
        stubToken("tok-1", 300L);
        server.expect(requestTo(ENDPOINT + "/internal/v1/files/9001/bind"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ownerType").value("USER_PROFILE"))
                .andExpect(jsonPath("$.ownerId").value("1001"))
                .andRespond(withSuccess("{\"status\":\"BOUND\"}", MediaType.APPLICATION_JSON));

        client.bindAvatar(1001L, 9001L);

        server.verify();
    }

    @Test
    void bindNon2xxPropagatesDependencyUnavailable() {
        stubToken("tok-1", 300L);
        server.expect(requestTo(ENDPOINT + "/internal/v1/files/9001/bind"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.bindAvatar(1001L, 9001L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(CommonErrorCode.DEPENDENCY_UNAVAILABLE);

        server.verify();
    }

    @Test
    void unbindSendsOwnerBody() {
        stubToken("tok-1", 300L);
        server.expect(requestTo(ENDPOINT + "/internal/v1/files/9001/unbind"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andExpect(jsonPath("$.ownerType").value("USER_PROFILE"))
                .andExpect(jsonPath("$.ownerId").value("1001"))
                .andRespond(withSuccess("{\"status\":\"UNBOUND\"}", MediaType.APPLICATION_JSON));

        client.unbindAvatar(1001L, 9001L);

        server.verify();
    }

    @Test
    void grantSendsBatchBodyAndParsesOnlyGrantedItems() {
        stubToken("tok-1", 300L);
        server.expect(requestTo(ENDPOINT + "/internal/v1/file-download-grants/batch"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.subjectType").value("USER"))
                .andExpect(jsonPath("$.subjectUserId").value(1001))
                .andExpect(jsonPath("$.purpose").value("PROFILE_AVATAR"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].requestKey").value("9001"))
                .andExpect(jsonPath("$.items[0].ownerType").value("USER_PROFILE"))
                .andExpect(jsonPath("$.items[0].ownerId").value("1001"))
                .andRespond(withSuccess(
                        "{\"items\":["
                                + "{\"requestKey\":\"9001\",\"fileId\":9001,\"status\":\"GRANTED\","
                                + "\"url\":\"http://bucket/avatar-9001\",\"expiresAt\":\"2026-08-22T10:05:00Z\"},"
                                + "{\"requestKey\":\"9002\",\"fileId\":9002,\"status\":\"UNAVAILABLE\","
                                + "\"url\":null,\"expiresAt\":null}"
                                + "]}",
                        MediaType.APPLICATION_JSON));

        Map<Long, String> urls = client.grantAvatarUrls(List.of(9001L, 9002L), 1001L);

        assertThat(urls).containsOnlyKeys(9001L);
        assertThat(urls.get(9001L)).isEqualTo("http://bucket/avatar-9001");
        server.verify();
    }

    @Test
    void grantUsesPerOwnerIdsForAdminBatch() {
        stubToken("tok-1", 300L);
        server.expect(requestTo(ENDPOINT + "/internal/v1/file-download-grants/batch"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].ownerId").value("1001"))
                .andRespond(withSuccess(
                        "{\"items\":[{\"requestKey\":\"9001\",\"fileId\":9001,\"status\":\"GRANTED\","
                                + "\"url\":\"http://bucket/avatar\",\"expiresAt\":\"2026-08-22T10:05:00Z\"}]}",
                        MediaType.APPLICATION_JSON));

        Map<Long, String> urls = client.grantAvatarUrls(
                List.of(9001L), 999L, Map.of(9001L, 1001L));

        assertThat(urls.get(9001L)).isEqualTo("http://bucket/avatar");
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

        client.bindAvatar(1001L, 9001L);
        client.bindAvatar(1001L, 9001L);
        verify(serviceTokenService, times(1)).issue(any(), any(), any(), any(), any(), any());

        // 过期前 30s 内视为过期：推进 271s（令牌 TTL 300s）后必须重新签发。
        clock.advance(Duration.ofSeconds(271L));
        client.bindAvatar(1001L, 9002L);
        verify(serviceTokenService, times(2)).issue(any(), any(), any(), any(), any(), any());
        server.verify();
    }


    @Test
    void concurrentTokenRefreshIssuesTokenOnlyOnce() throws Exception {
        // 首次令牌缓存为空：两个线程同时进入首次检查并阻塞在 issue 内，
        // 复现 B2 竞态 —— 旧实现两个线程都会调用 issue。
        CountDownLatch enterIssue = new CountDownLatch(1);
        CountDownLatch releaseIssue = new CountDownLatch(1);
        when(serviceTokenService.issue(
                eq("user-service"), eq("s3cret"), eq("educloud-file"),
                eq(List.of("file:internal")), isNull(), isNull()))
                .thenAnswer(invocation -> {
                    enterIssue.countDown();
                    releaseIssue.await(5, TimeUnit.SECONDS);
                    return new ServiceTokenService.IssueResult("tok-1", 300L);
                });

        server.expect(requestTo(anything()))
                .andRespond(withSuccess("{\"status\":\"BOUND\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(anything()))
                .andRespond(withSuccess("{\"status\":\"BOUND\"}", MediaType.APPLICATION_JSON));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> client.bindAvatar(1001L, 9001L));
            Future<?> second = pool.submit(() -> client.bindAvatar(1002L, 9002L));
            assertThat(enterIssue.await(2, TimeUnit.SECONDS)).isTrue();
            releaseIssue.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        verify(serviceTokenService, times(1)).issue(any(), any(), any(), any(), any(), any());
        server.verify();
    }

    @Test
    void disabledClientIsNoOp() {
        FileClient disabled = new FileClient(
                new FileClientProperties(ENDPOINT, "user-service", "s3cret", false, Duration.ofSeconds(3)),
                serviceTokenService,
                RestClient.builder().baseUrl(ENDPOINT).build(),
                clock);

        disabled.bindAvatar(1001L, 9001L);
        disabled.unbindAvatar(1001L, 9001L);

        assertThat(disabled.grantAvatarUrls(List.of(9001L), 1001L)).isEmpty();
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
