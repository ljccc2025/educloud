package com.educloud.search.security;

import com.educloud.search.config.SearchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalApiFilterTest {

    private JwtDecoder jwtDecoder;
    private InternalApiFilter filter;
    private SearchProperties properties;

    @BeforeEach
    void setUp() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        jwtDecoder = mock(JwtDecoder.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JwtDecoder> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jwtDecoder);

        properties = new SearchProperties();
        properties.getInternal().setAudience("educloud-search");
        properties.getInternal().setAllowedClientIds(List.of("educloud-course", "educloud-content", "gateway"));
        properties.getInternal().setSecretToken("my-secret-token");

        filter = new InternalApiFilter(provider, properties);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private Jwt serviceToken(String clientId, List<String> audiences) {
        return new Jwt(
                "token",
                Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of("clientId", clientId, "aud", audiences)
        );
    }

    @Test
    @DisplayName("使用正确的 X-Internal-Token 头直接认证放行")
    void testAllowsValidSecretTokenHeader() throws Exception {
        MockHttpServletRequest request = internalRequest("/internal/v1/search/sync");
        request.addHeader("X-Internal-Token", "my-secret-token");
        request.addHeader("X-Client-Id", "educloud-course");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(request.getAttribute(InternalApiFilter.CLIENT_ID_ATTRIBUTE)).isEqualTo("educloud-course");
    }

    @Test
    @DisplayName("使用合法的微服务 Bearer Token 且在 Client ID 白名单内正常放行")
    void testAllowsWhiteListedClientWithBearerToken() throws Exception {
        when(jwtDecoder.decode("valid_jwt")).thenReturn(serviceToken("educloud-content", List.of("educloud-search")));

        MockHttpServletRequest request = internalRequest("/internal/v1/search/sync");
        request.addHeader("Authorization", "Bearer valid_jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(request.getAttribute(InternalApiFilter.CLIENT_ID_ATTRIBUTE)).isEqualTo("educloud-content");
    }

    @Test
    @DisplayName("JWT 解码失败返回 401 UNAUTHORIZED")
    void testRejectsDecodeFailureWith401() throws Exception {
        when(jwtDecoder.decode("bad_token")).thenThrow(new JwtException("invalid token"));

        MockHttpServletRequest request = internalRequest("/internal/v1/search/sync");
        request.addHeader("Authorization", "Bearer bad_token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(request.getAttribute(InternalApiFilter.CLIENT_ID_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("未在白名单的微服务 Client ID 返回 403 FORBIDDEN")
    void testRejectsNonWhiteListedClientWith403() throws Exception {
        when(jwtDecoder.decode("jwt_evil")).thenReturn(serviceToken("unauthorized-client", List.of("educloud-search")));

        MockHttpServletRequest request = internalRequest("/internal/v1/search/sync");
        request.addHeader("Authorization", "Bearer jwt_evil");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("非 /internal/ 路径请求跳过此过滤器")
    void testSkipsFilterWhenPathIsNotInternal() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/search/courses");
        request.setServletPath("/api/v1/search/courses");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    private static MockHttpServletRequest internalRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        return request;
    }
}
