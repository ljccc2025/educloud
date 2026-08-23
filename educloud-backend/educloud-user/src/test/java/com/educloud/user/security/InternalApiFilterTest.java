package com.educloud.user.security;

import com.educloud.user.config.InternalProperties;
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

/** 内部接口过滤器单元测试（验签/aud/clientId 白名单）。 */
class InternalApiFilterTest {

    private JwtDecoder jwtDecoder;
    private InternalApiFilter filter;

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);
        filter = new InternalApiFilter(
                jwtDecoder,
                new InternalProperties("bootstrap-key", List.of("order-service"), "educloud-user"));
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
        when(jwtDecoder.decode("abc")).thenReturn(serviceToken("order-service", List.of("educloud-user")));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/users/1/status-snapshot");
        request.addHeader("Authorization", "Bearer abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(InternalApiFilter.CLIENT_ID_ATTRIBUTE)).isEqualTo("order-service");
    }

    @Test
    void skipsServiceTokenEndpointWhichUsesHttpBasic() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/internal/v1/service-tokens");
        request.addHeader("Authorization", "Basic dXNlcjpzZWNyZXQ=");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(request.getAttribute(InternalApiFilter.CLIENT_ID_ATTRIBUTE)).isNull();
    }

    @Test
    void rejectsMissingTokenAndNonWhiteListedClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/users/1/status-snapshot");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(401);

        when(jwtDecoder.decode("abc")).thenReturn(serviceToken("evil-service", List.of("educloud-user")));
        request = new MockHttpServletRequest("GET", "/internal/v1/users/1/status-snapshot");
        request.addHeader("Authorization", "Bearer abc");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(403);
    }
}
