package com.educloud.user.security;

import com.educloud.user.config.RegistrationRateLimitProperties;
import com.educloud.user.config.SessionProperties;
import com.educloud.user.testcontainers.TestContainerImages;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注册限流集成测试（真实 Redis）。
 * 依据：M03 注册限流设计 4.1 节；VM/CI 上以 -Pintegration 执行。
 */
@Testcontainers
class RegistrationRateLimitIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(TestContainerImages.redis())
            .withExposedPorts(6379);

    private static RegistrationRateLimitFilter filter;

    @BeforeAll
    static void connect() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        RegistrationRateLimitProperties properties = new RegistrationRateLimitProperties();
        properties.setEnabled(true);
        properties.setIpMaxAttempts(5);
        properties.setDeviceMaxAttempts(3);
        properties.setWindow(Duration.ofMinutes(5));
        properties.setRedisKeyPrefix("it:" + java.util.UUID.randomUUID().toString().substring(0, 8) + ":{env}:ratelimit");
        // SessionProperties 是 record：environment 用构造器传入。
        SessionProperties session = new SessionProperties(
                "test", Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofSeconds(5), false);
        filter = new RegistrationRateLimitFilter(redis, properties, session, new ObjectMapper());
    }

    private MockHttpServletResponse hit(String deviceId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.setRemoteAddr("10.1.1.1");
        if (deviceId != null) {
            request.addHeader("X-Device-Id", deviceId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));
        return response;
    }

    @Test
    void sixthIpRequestWithinWindowIs429() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(hit(null).getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse sixth = hit(null);
        assertThat(sixth.getStatus()).isEqualTo(429);
        assertThat(sixth.getHeader("Retry-After")).isNotBlank();
    }

    @Test
    void deviceLimitKicksInEarlier() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(hit("device-x").getStatus()).isEqualTo(200);
        }
        assertThat(hit("device-x").getStatus()).isEqualTo(429);
    }
}
