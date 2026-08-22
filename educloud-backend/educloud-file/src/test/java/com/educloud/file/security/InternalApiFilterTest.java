package com.educloud.file.security;

import com.educloud.file.config.FileProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 内部接口过滤器单元测试（验签/aud/clientId 白名单），复制 user 版适配 File。 */
class InternalApiFilterTest {

    private JwtDecoder jwtDecoder;
    private InternalApiFilter filter;

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);
        filter = new InternalApiFilter(jwtDecoder, properties());
    }

    private static FileProperties properties() {
        return new FileProperties(
                new FileProperties.Storage("http://127.0.0.1:9000", "ak", "sk", "educloud-files"),
                new FileProperties.Upload(10485760, List.of("image/jpeg"), Duration.ofMinutes(5), Duration.ofMinutes(15)),
                new FileProperties.DownloadGrant(Duration.ofMinutes(5), Duration.ofMinutes(15), List.of("PROFILE_AVATAR")),
                new FileProperties.Cleanup(Duration.ofHours(24), Duration.ofMinutes(15), 50),
                new FileProperties.StorageTest(1, Duration.ofMinutes(1)),
                new FileProperties.Internal("bootstrap-key", List.of("user-service"), "educloud-file"),
                new FileProperties.Jwt("file:/jwks.json", "https://issuer.educloud.local", "educloud-api"),
                    "local");
    }

    private Jwt serviceToken(String clientId, List<String> audiences) {
        return new Jwt(
                "token",
                Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of("clientId", clientId, "aud", audiences));
    }

    @Test
    void allowsWhiteListedClientWithCorrectAudience() throws Exception {
        when(jwtDecoder.decode("abc")).thenReturn(serviceToken("user-service", List.of("educloud-file")));
        MockHttpServletRequest request = internalRequest("/internal/v1/files/1/availability");
        request.addHeader("Authorization", "Bearer abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(InternalApiFilter.CLIENT_ID_ATTRIBUTE)).isEqualTo("user-service");
    }

    @Test
    void rejectsMissingTokenWrongAudienceAndNonWhiteListedClient() throws Exception {
        MockHttpServletRequest request = internalRequest("/internal/v1/files/1/availability");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(401);

        when(jwtDecoder.decode("abc")).thenReturn(serviceToken("user-service", List.of("educloud-user")));
        request = internalRequest("/internal/v1/files/1/availability");
        request.addHeader("Authorization", "Bearer abc");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(403);

        when(jwtDecoder.decode("def")).thenReturn(serviceToken("evil-service", List.of("educloud-file")));
        request = internalRequest("/internal/v1/files/1/availability");
        request.addHeader("Authorization", "Bearer def");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void skipsFilterWhenServletPathIsNotInternal() throws Exception {
        // requestURI 含 /internal/v1 但 servletPath 不匹配（如经 context-path/rewrite 映射）→ 不拦截
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/files/1/availability");
        request.setServletPath("/public/files/1/availability");
        request.addHeader("Authorization", "Bearer nope");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(InternalApiFilter.CLIENT_ID_ATTRIBUTE)).isNull();
    }

    /** 内部接口请求：servletPath 与 requestURI 对齐（真实部署中无 context-path 时两者一致）。 */
    private static MockHttpServletRequest internalRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }
}
