package com.educloud.user.security;

import com.educloud.common.error.CommonErrorCode;
import com.educloud.user.config.RegistrationRateLimitProperties;
import com.educloud.user.config.SessionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class RegistrationRateLimitFilterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RegistrationRateLimitProperties properties;
    private RegistrationRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        properties = new RegistrationRateLimitProperties();
        properties.setEnabled(true);
        properties.setIpMaxAttempts(5);
        properties.setDeviceMaxAttempts(3);
        properties.setWindow(Duration.ofMinutes(5));
        properties.setRedisKeyPrefix("educloud:{env}:ratelimit");
        SessionProperties session = mock(SessionProperties.class);
        when(session.environment()).thenReturn("test");
        filter = new RegistrationRateLimitFilter(redis, properties, session, new ObjectMapper());
    }

    private MockHttpServletRequest registerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.setRemoteAddr("10.0.0.1");
        return request;
    }

    @Test
    void allowsRequestWithinIpLimit() throws Exception {
        when(values.increment(anyString())).thenReturn(1L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(registerRequest(), response, (req, res) -> {
            ((MockHttpServletResponse) res).setStatus(200);
        });
        assertThat(response.getStatus()).isEqualTo(200);
        verify(values).increment(argThat(key -> key.startsWith("educloud:test:ratelimit:register-ip:")));
    }

    @Test
    void rejectsWhenIpLimitExceeded() throws Exception {
        when(values.increment(anyString())).thenReturn(6L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(registerRequest(), response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotBlank();
        assertThat(response.getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void rejectsWhenDeviceLimitExceeded() throws Exception {
        when(values.increment(anyString())).thenReturn(4L);
        MockHttpServletRequest request = registerRequest();
        request.addHeader("X-Device-Id", "device-abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void fallsBackToIpOnlyWhenDeviceHeaderMissing() throws Exception {
        when(values.increment(anyString())).thenReturn(1L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(registerRequest(), response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        verify(values, times(1)).increment(anyString());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void returns503WhenRedisFails() throws Exception {
        when(values.increment(anyString())).thenThrow(new RuntimeException("redis down"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(registerRequest(), response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("DEPENDENCY_UNAVAILABLE");
    }

    @Test
    void ignoresNonRegisterPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        verify(values, never()).increment(anyString());
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
