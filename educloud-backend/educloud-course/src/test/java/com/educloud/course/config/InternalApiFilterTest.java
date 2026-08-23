package com.educloud.course.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 内部接口过滤器单元测试（验签/aud=educloud-course/clientId 白名单），复制 file 版适配 Course。 */
class InternalApiFilterTest {

    private JwtDecoder jwtDecoder;
    private InternalApiFilter filter;

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);
        filter = new InternalApiFilter(jwtDecoder, properties());
    }

    private static CourseProperties properties() {
        return new CourseProperties(
                "test",
                new CourseProperties.Jwt("classpath:jwks-test.json", "https://issuer.educloud.local", "educloud-api"),
                new CourseProperties.Internal(List.of("educloud-content"), "educloud-course"));
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
        when(jwtDecoder.decode("abc")).thenReturn(serviceToken("educloud-content", List.of("educloud-course")));
        MockHttpServletRequest request = internalRequest("/internal/v1/courses/1");
        request.addHeader("Authorization", "Bearer abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(InternalApiFilter.CLIENT_ID_ATTRIBUTE)).isEqualTo("educloud-content");
    }

    @Test
    void rejectsMissingTokenWrongAudienceAndNonWhiteListedClient() throws Exception {
        MockHttpServletRequest request = internalRequest("/internal/v1/courses/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(401);

        when(jwtDecoder.decode("abc")).thenReturn(serviceToken("educloud-content", List.of("educloud-user")));
        request = internalRequest("/internal/v1/courses/1");
        request.addHeader("Authorization", "Bearer abc");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(403);

        when(jwtDecoder.decode("def")).thenReturn(serviceToken("evil-service", List.of("educloud-course")));
        request = internalRequest("/internal/v1/courses/1");
        request.addHeader("Authorization", "Bearer def");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void skipsFilterWhenServletPathIsNotInternal() throws Exception {
        // requestURI 含 /internal/v1 但 servletPath 不匹配（如经 context-path/rewrite 映射）→ 不拦截
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/courses/1");
        request.setServletPath("/public/courses/1");
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