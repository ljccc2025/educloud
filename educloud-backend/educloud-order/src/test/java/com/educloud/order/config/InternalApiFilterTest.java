package com.educloud.order.config;

import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JwtDecoder> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jwtDecoder);
        filter = new InternalApiFilter(provider, properties());
    }

    private static OrderProperties properties() {
        return new OrderProperties(
                "test",
                new OrderProperties.JwtProperties("", "https://issuer.educloud.local", "educloud-api"),
                new OrderProperties.InternalProperties("educloud-course,educloud-payment", "educloud-order", "test-secret"));
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
        when(jwtDecoder.decode("abc")).thenReturn(serviceToken("educloud-course", List.of("educloud-order")));
        MockHttpServletRequest request = internalRequest("/internal/v1/orders/1/payable-snapshot");
        request.addHeader("Authorization", "Bearer abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(request.getAttribute(InternalApiFilter.CLIENT_ID_ATTRIBUTE)).isEqualTo("educloud-course");
    }

    @Test
    void allowsInternalSecretTokenHeader() throws Exception {
        MockHttpServletRequest request = internalRequest("/internal/v1/orders/1/payable-snapshot");
        request.addHeader("X-Internal-Token", "test-secret");
        request.addHeader("X-Client-Id", "educloud-payment");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(request.getAttribute(InternalApiFilter.CLIENT_ID_ATTRIBUTE)).isEqualTo("educloud-payment");
    }

    @Test
    void rejectsDecodeFailureWith401() throws Exception {
        when(jwtDecoder.decode("bad")).thenThrow(new JwtException("invalid signature"));
        MockHttpServletRequest request = internalRequest("/internal/v1/orders/1/payable-snapshot");
        request.addHeader("Authorization", "Bearer bad");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(request.getAttribute(InternalApiFilter.CLIENT_ID_ATTRIBUTE)).isNull();
    }

    @Test
    void rejectsMissingTokenWrongAudienceAndNonWhiteListedClient() throws Exception {
        MockHttpServletRequest request = internalRequest("/internal/v1/orders/1/payable-snapshot");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(401);

        when(jwtDecoder.decode("abc")).thenReturn(serviceToken("educloud-course", List.of("educloud-user")));
        request = internalRequest("/internal/v1/orders/1/payable-snapshot");
        request.addHeader("Authorization", "Bearer abc");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(403);

        when(jwtDecoder.decode("def")).thenReturn(serviceToken("evil-service", List.of("educloud-order")));
        request = internalRequest("/internal/v1/orders/1/payable-snapshot");
        request.addHeader("Authorization", "Bearer def");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void skipsFilterWhenServletPathIsNotInternal() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/orders/1");
        request.setServletPath("/api/v1/orders/1");
        request.addHeader("Authorization", "Bearer nope");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(InternalApiFilter.CLIENT_ID_ATTRIBUTE)).isNull();
    }

    private static MockHttpServletRequest internalRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }
}
