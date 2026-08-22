package com.educloud.file.security;

import com.educloud.file.config.FileProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** storage-tests 限频过滤器单元测试：第 1 次放行、超限 429、Redis 失败关闭 503。 */
class StorageTestRateLimitFilterTest {

    private JwtDecoder jwtDecoder;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private FileProperties properties;
    private StorageTestRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        properties = new FileProperties(
                new FileProperties.Storage("http://127.0.0.1:9000", "ak", "sk", "educloud-files"),
                new FileProperties.Upload(10485760, List.of("image/jpeg"), Duration.ofMinutes(5), Duration.ofMinutes(15)),
                new FileProperties.DownloadGrant(Duration.ofMinutes(5), Duration.ofMinutes(15), List.of("PROFILE_AVATAR")),
                new FileProperties.Cleanup(Duration.ofHours(24), Duration.ofMinutes(15), 50),
                new FileProperties.StorageTest(1, Duration.ofMinutes(1)),
                new FileProperties.Internal("bootstrap-key", List.of("user-service"), "educloud-file"),
                new FileProperties.Jwt("file:/jwks.json", "https://issuer.educloud.local", "educloud-api"),
                    "local");
        filter = new StorageTestRateLimitFilter(jwtDecoder, redis, properties, new ObjectMapper());
    }

    private FileProperties withEnvironment(String environment) {
        return new FileProperties(
                new FileProperties.Storage("http://127.0.0.1:9000", "ak", "sk", "educloud-files"),
                new FileProperties.Upload(10485760, List.of("image/jpeg"), Duration.ofMinutes(5), Duration.ofMinutes(15)),
                new FileProperties.DownloadGrant(Duration.ofMinutes(5), Duration.ofMinutes(15), List.of("PROFILE_AVATAR")),
                new FileProperties.Cleanup(Duration.ofHours(24), Duration.ofMinutes(15), 50),
                new FileProperties.StorageTest(1, Duration.ofMinutes(1)),
                new FileProperties.Internal("bootstrap-key", List.of("user-service"), "educloud-file"),
                new FileProperties.Jwt("file:/jwks.json", "https://issuer.educloud.local", "educloud-api"),
                environment);
    }

    private MockHttpServletRequest storageTestRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/files/storage-tests");
        request.setRemoteAddr("10.0.0.1");
        return request;
    }

    @Test
    void allowsFirstRequestWithinLimit() throws Exception {
        when(values.increment(anyString())).thenReturn(1L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(storageTestRequest(), response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertThat(response.getStatus()).isEqualTo(200);
        verify(values).increment("educloud:local:file:ratelimit:storage-test:ip:10.0.0.1");
        verify(redis).expire(anyString(), any(Duration.class));
    }

    @Test
    void usesEnvironmentNamespacedRedisKey() throws Exception {
        when(values.increment(anyString())).thenReturn(1L);
        StorageTestRateLimitFilter prodFilter = new StorageTestRateLimitFilter(
                jwtDecoder, redis, withEnvironment("prod"), new ObjectMapper());
        MockHttpServletResponse response = new MockHttpServletResponse();
        prodFilter.doFilter(storageTestRequest(), response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertThat(response.getStatus()).isEqualTo(200);
        verify(values).increment("educloud:prod:file:ratelimit:storage-test:ip:10.0.0.1");
    }

    @Test
    void rejectsWhenLimitExceeded() throws Exception {
        when(values.increment(anyString())).thenReturn(2L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(storageTestRequest(), response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotBlank();
        assertThat(response.getContentAsString()).contains("STORAGE_TEST_RATE_LIMITED");
    }

    @Test
    void returns503WhenRedisThrows() throws Exception {
        when(values.increment(anyString())).thenThrow(new RuntimeException("redis down"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(storageTestRequest(), response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("DEPENDENCY_UNAVAILABLE");
    }

    @Test
    void returns503WhenRedisReturnsNull() throws Exception {
        when(values.increment(anyString())).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(storageTestRequest(), response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("DEPENDENCY_UNAVAILABLE");
    }

    @Test
    void ignoresNonPostAndOtherPaths() throws Exception {
        MockHttpServletRequest getRequest = new MockHttpServletRequest("GET", "/api/v1/files/storage-tests");
        getRequest.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(getRequest, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertThat(response.getStatus()).isEqualTo(200);
        verify(values, never()).increment(anyString());
    }
}
