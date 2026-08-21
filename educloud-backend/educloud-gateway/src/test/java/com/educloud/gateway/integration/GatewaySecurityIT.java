package com.educloud.gateway.integration;

import com.educloud.gateway.config.GatewayRateLimitProperties;
import com.educloud.gateway.config.GatewayRuntimeProperties;
import com.educloud.gateway.config.GatewaySecurityProperties;
import com.educloud.gateway.config.GatewayWebProperties;
import com.educloud.gateway.error.GatewayAccessDeniedHandler;
import com.educloud.gateway.error.GatewayAuthenticationEntryPoint;
import com.educloud.gateway.error.GatewayErrorWriter;
import com.educloud.gateway.observability.GatewayMetrics;
import com.educloud.gateway.ratelimit.GatewayRateLimitWebFilter;
import com.educloud.gateway.ratelimit.HmacKeyHasher;
import com.educloud.gateway.ratelimit.LoginNameExtractor;
import com.educloud.gateway.ratelimit.RedisTokenBucketLimiter;
import com.educloud.gateway.route.AccessPolicy;
import com.educloud.gateway.route.InternalPathWebFilter;
import com.educloud.gateway.security.IdentityHeaderWebFilter;
import com.educloud.gateway.security.JwksState;
import com.educloud.gateway.security.JwtDecoderConfiguration;
import com.educloud.gateway.security.RedisSessionVerifier;
import com.educloud.gateway.security.SecurityConfiguration;
import com.educloud.gateway.security.TestJwtKeys;
import com.educloud.gateway.web.ClientIpResolver;
import com.educloud.gateway.web.CorsConfiguration;
import com.educloud.gateway.web.OriginPolicyWebFilter;
import com.educloud.gateway.web.RequestBodyCachingWebFilter;
import com.educloud.gateway.web.RequestIdWebFilter;
import com.educloud.gateway.web.SecurityHeadersWebFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GatewaySecurityIT {

    private static final DockerImageName REDIS_IMAGE = TestContainerImages.redis();
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(5);
    private static final String ISSUER = "https://issuer.educloud.local";
    private static final String AUDIENCE = "educloud-api";
    private static final String SUBJECT = "gateway-it-user";
    private static final String SESSION_ID = "gateway-it-session";
    private static final long TOKEN_VERSION = 3L;

    private final String environment = environment();
    private final TestJwtKeys signingKeys = new TestJwtKeys();
    private final TestJwtKeys unknownKeys = new TestJwtKeys();
    private final AtomicReference<Map<String, String>> downstreamHeaders = new AtomicReference<>(Map.of());
    private final IntegrationResourceTracker resources = new IntegrationResourceTracker();
    private GenericContainer<?> redisContainer;
    private org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory connectionFactory;
    private org.springframework.data.redis.core.ReactiveStringRedisTemplate redis;
    private DisposableServer downstream;
    private GatewayRuntime gateway;
    private AnnotationConfigApplicationContext decoderContext;

    @BeforeAll
    void startInfrastructureAndGateway() {
        redisContainer = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
        redisContainer.start();
        resources.track("redis-container:" + environment, redisContainer::stop);
        connectionFactory = connectionFactory(redisContainer);
        resources.track("redis-connection:" + environment, connectionFactory::destroy);
        redis = new org.springframework.data.redis.core.ReactiveStringRedisTemplate(connectionFactory);
        downstream = startDownstream();
        resources.track("downstream:" + environment, downstream::disposeNow);
        gateway = startGateway(redis, environment);
        resources.track("gateway:" + environment, gateway::close);
    }

    @AfterEach
    void clearOwnedRedisKeys() {
        List<String> keys = redis.scan(org.springframework.data.redis.core.ScanOptions.scanOptions()
                        .match("educloud:{" + environment + ":*")
                        .count(200)
                        .build())
                .collectList()
                .block(BLOCK_TIMEOUT);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(Flux.fromIterable(keys)).block(BLOCK_TIMEOUT);
        }
        assertThat(redis.scan(org.springframework.data.redis.core.ScanOptions.scanOptions()
                        .match("educloud:{" + environment + ":*")
                        .build()).collectList().block(BLOCK_TIMEOUT)).isEmpty();
        downstreamHeaders.set(Map.of());
    }

    @AfterAll
    void stopInfrastructure() {
        resources.close();
    }

    @Test
    void enforcesOptionalBearerJwtAndAuthoritativeSessionSemantics() {
        gateway.client().get().uri("/api/v1/courses")
                .exchange().expectStatus().isOk();
        SafeHttpResponse malformed = gateway.authorizedGet("/api/v1/courses", "malformed", Map.of());
        assertThat(malformed.status()).isEqualTo(401);
        assertThat(malformed.body()).contains("\"code\":\"UNAUTHENTICATED\"");
        assertThat(gateway.authorizedGet("/api/v1/courses", expiredToken(), Map.of()).status())
                .isEqualTo(401);
        assertThat(gateway.authorizedGet(
                "/api/v1/courses", unknownKeys.signedToken(validClaims()), Map.of()).status())
                .isEqualTo(401);
        gateway.client().get().uri("/api/v1/users/me")
                .exchange().expectStatus().isUnauthorized();

        writeSession("REVOKED");
        assertThat(gateway.authorizedGet("/api/v1/courses", validToken(), Map.of()).status())
                .isEqualTo(401);

        writeSession("ACTIVE");
        String token = validToken();
        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        SafeHttpResponse response = gateway.authorizedGet("/api/v1/users/me", token, Map.of(
                "X-Request-Id", "gateway-security-it",
                "traceparent", traceparent,
                "Forwarded", "for=203.0.113.10",
                "X-User-Id", "forged-user",
                "X-EduCloud-Identity-Test", "forged"));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers())
                .containsEntry("X-Request-Id", "gateway-security-it")
                .containsEntry("X-Content-Type-Options", "nosniff");

        Map<String, String> observed = downstreamHeaders.get();
        assertThat(secretEquals(observed.get(HttpHeaders.AUTHORIZATION), "Bearer " + token)).isTrue();
        Map<String, String> nonSecretObserved = new LinkedHashMap<>(observed);
        nonSecretObserved.remove(HttpHeaders.AUTHORIZATION);
        assertThat(nonSecretObserved)
                .containsEntry("X-Request-Id", "gateway-security-it")
                .containsEntry("traceparent", traceparent)
                .doesNotContainKeys("Forwarded", "X-User-Id", "X-EduCloud-Identity-Test");
    }

    @Test
    void appliesCorsBodySecurityHeaderRateLimitAndPassThroughContracts() {
        gateway.client().get().uri("/api/v1/courses")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                .expectHeader().valueEquals("X-Frame-Options", "DENY");
        gateway.client().get().uri("/api/v1/courses")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173.evil.example")
                .exchange().expectStatus().isForbidden();
        gateway.client().post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("x".repeat(256))
                .exchange().expectStatus().isEqualTo(413);
        gateway.client().get().uri("/internal/v1/secret")
                .exchange().expectStatus().isNotFound();

        writeSession("ACTIVE");
        SafeHttpResponse downstreamError = gateway.authorizedGet(
                "/api/v1/users/business-error", validToken(), Map.of());
        assertThat(downstreamError.status()).isEqualTo(422);
        assertThat(downstreamError.headers().get(HttpHeaders.CONTENT_TYPE))
                .startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(downstreamError.body()).isEqualTo("{\"code\":\"DOWNSTREAM_RULE\"}");

        WebTestClient.ResponseSpec last = null;
        for (int request = 0; request < 21; request++) {
            last = gateway.client().get().uri("/api/v1/courses").exchange();
        }
        last.expectStatus().isEqualTo(429)
                .expectHeader().exists(HttpHeaders.RETRY_AFTER)
                .expectBody().jsonPath("$.code").isEqualTo("GATEWAY_RATE_LIMITED");
    }

    @Test
    void treatsRedisFailureAsFailOpenOnlyForAnonymousPublicTraffic() {
        GenericContainer<?> isolatedContainer = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
        isolatedContainer.start();
        var isolatedFactory = connectionFactory(isolatedContainer);
        var isolatedRedis = new org.springframework.data.redis.core.ReactiveStringRedisTemplate(isolatedFactory);
        GatewayRuntime isolatedGateway = startGateway(isolatedRedis, environment());
        try {
            isolatedContainer.stop();

            isolatedGateway.client().get().uri("/api/v1/courses")
                    .exchange().expectStatus().isOk();
            assertThat(isolatedGateway.authorizedGet(
                    "/api/v1/courses", validToken(), Map.of()).status()).isEqualTo(503);
            assertThat(isolatedGateway.authorizedGet(
                    "/api/v1/users/me", validToken(), Map.of()).status()).isEqualTo(503);
        } finally {
            isolatedGateway.close();
            isolatedFactory.destroy();
            if (isolatedContainer.isRunning()) {
                isolatedContainer.stop();
            }
        }
    }

    private GatewayRuntime startGateway(
            org.springframework.data.redis.core.ReactiveStringRedisTemplate redisTemplate,
            String runtimeEnvironment) {
        ObjectMapper objectMapper = objectMapper();
        GatewayErrorWriter writer = new GatewayErrorWriter(objectMapper, Clock.systemUTC());
        GatewayWebProperties web = webProperties();
        GatewayRateLimitProperties rate = rateProperties();
        GatewayRuntimeProperties runtime = new GatewayRuntimeProperties(runtimeEnvironment);
        AccessPolicy accessPolicy = AccessPolicy.standard();
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("gatewayMetrics", GatewayMetrics.noOp());

        ReactiveJwtDecoder jwtDecoder = decoder();
        var entryPoint = new GatewayAuthenticationEntryPoint(
                writer, beans.getBeanProvider(GatewayMetrics.class));
        var deniedHandler = new GatewayAccessDeniedHandler(
                writer, beans.getBeanProvider(GatewayMetrics.class));
        SecurityWebFilterChain security = new SecurityConfiguration().gatewaySecurityFilterChain(
                ServerHttpSecurity.http(), jwtDecoder, accessPolicy,
                new RedisSessionVerifier(redisTemplate, runtime), writer, GatewayMetrics.noOp(),
                entryPoint, deniedHandler);
        RedisTokenBucketLimiter limiter = new RedisTokenBucketLimiter(redisTemplate, runtime);
        GatewayRateLimitWebFilter rateLimit = new GatewayRateLimitWebFilter(
                accessPolicy,
                limiter,
                new HmacKeyHasher(rate),
                new LoginNameExtractor(objectMapper),
                rate,
                writer,
                beans.getBeanProvider(GatewayMetrics.class));

        List<WebFilter> filters = List.of(
                new RequestIdWebFilter(),
                new SecurityHeadersWebFilter(runtime),
                new InternalPathWebFilter(accessPolicy, writer),
                new RequestBodyCachingWebFilter(accessPolicy, web, writer),
                new IdentityHeaderWebFilter(new ClientIpResolver(web), writer),
                new OriginPolicyWebFilter(web, writer),
                new CorsConfiguration().gatewayCorsWebFilter(web),
                rateLimit,
                new WebFilterChainProxy(security));
        HttpHandler handler = WebHttpHandlerBuilder.webHandler(proxyHandler())
                .filters(registered -> registered.addAll(filters))
                .build();
        DisposableServer server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle(new ReactorHttpHandlerAdapter(handler))
                .bindNow();
        WebTestClient client = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + server.port())
                .responseTimeout(Duration.ofSeconds(5))
                .build();
        HttpClient safeHttpClient = HttpClient.create()
                .baseUrl("http://127.0.0.1:" + server.port())
                .responseTimeout(Duration.ofSeconds(5));
        return new GatewayRuntime(server, client, safeHttpClient);
    }

    private WebHandler proxyHandler() {
        WebClient downstreamClient = WebClient.create("http://127.0.0.1:" + downstream.port());
        return exchange -> downstreamClient
                .method(exchange.getRequest().getMethod())
                .uri(exchange.getRequest().getURI().getRawPath())
                .headers(headers -> {
                    headers.addAll(exchange.getRequest().getHeaders());
                    headers.remove(HttpHeaders.HOST);
                })
                .body(BodyInserters.fromDataBuffers(exchange.getRequest().getBody()))
                .exchangeToMono(response -> response.bodyToMono(byte[].class)
                        .defaultIfEmpty(new byte[0])
                        .flatMap(body -> {
                            exchange.getResponse().setStatusCode(
                                    HttpStatusCode.valueOf(response.statusCode().value()));
                            response.headers().contentType().ifPresent(
                                    exchange.getResponse().getHeaders()::setContentType);
                            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
                            return exchange.getResponse().writeWith(Mono.just(buffer));
                        }));
    }

    private DisposableServer startDownstream() {
        return HttpServer.create().host("127.0.0.1").port(0)
                .handle((request, response) -> {
                    Map<String, String> headers = new LinkedHashMap<>();
                    for (String name : List.of(
                            HttpHeaders.AUTHORIZATION,
                            "X-Request-Id",
                            "traceparent",
                            "Forwarded",
                            "X-User-Id",
                            "X-EduCloud-Identity-Test")) {
                        String value = request.requestHeaders().get(name);
                        if (value != null) {
                            headers.put(name, value);
                        }
                    }
                    downstreamHeaders.set(Map.copyOf(headers));
                    boolean businessError = request.uri().startsWith("/api/v1/users/business-error");
                    response.status(businessError ? 422 : 200);
                    response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                    return response.sendString(Mono.just(businessError
                            ? "{\"code\":\"DOWNSTREAM_RULE\"}"
                            : "{\"data\":\"ok\"}"));
                })
                .bindNow();
    }

    private ReactiveJwtDecoder decoder() {
        if (decoderContext == null) {
            GatewaySecurityProperties properties = new GatewaySecurityProperties();
            properties.setJwksJson(signingKeys.publicJwksJson());
            properties.setIssuer(ISSUER);
            properties.setAudience(AUDIENCE);
            properties.setClockSkew(Duration.ofSeconds(5));
            decoderContext = new AnnotationConfigApplicationContext();
            decoderContext.registerBean(GatewaySecurityProperties.class, () -> properties);
            decoderContext.registerBean(Clock.class, Clock::systemUTC);
            decoderContext.registerBean(JwksState.class, JwksState::new);
            decoderContext.register(JwtDecoderConfiguration.class);
            decoderContext.refresh();
            resources.track("jwt-decoder:" + environment, decoderContext::close);
        }
        return decoderContext.getBean(ReactiveJwtDecoder.class);
    }

    private void writeSession(String status) {
        String key = "educloud:{" + environment + ":auth}:session:" + SESSION_ID;
        redis.opsForHash().putAll(key, Map.of(
                "subject", SUBJECT,
                "status", status,
                "tokenVersion", Long.toString(TOKEN_VERSION))).block(BLOCK_TIMEOUT);
        assertThat(redis.expire(key, Duration.ofMinutes(5)).block(BLOCK_TIMEOUT)).isTrue();
    }

    private String validToken() {
        return signingKeys.signedToken(validClaims());
    }

    private String expiredToken() {
        Map<String, Object> claims = validClaims();
        Instant now = Instant.now();
        claims.put("iat", now.minusSeconds(300));
        claims.put("nbf", now.minusSeconds(300));
        claims.put("exp", now.minusSeconds(120));
        return signingKeys.signedToken(claims);
    }

    private static Map<String, Object> validClaims() {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", ISSUER);
        claims.put("aud", List.of(AUDIENCE));
        claims.put("sub", SUBJECT);
        claims.put("sid", SESSION_ID);
        claims.put("userType", "STUDENT");
        claims.put("tokenVersion", TOKEN_VERSION);
        claims.put("roles", List.of("STUDENT"));
        claims.put("permissions", List.of("course:read"));
        claims.put("iat", now.minusSeconds(1));
        claims.put("nbf", now.minusSeconds(1));
        claims.put("exp", now.plusSeconds(300));
        return claims;
    }

    private static GatewayWebProperties webProperties() {
        GatewayWebProperties properties = new GatewayWebProperties();
        properties.setAllowedOrigins(List.of("http://localhost:5173"));
        properties.setTrustedProxyCidrs(List.of());
        properties.setTrustedProxyHops(1);
        properties.setGlobalBodyLimit(DataSize.ofKilobytes(1));
        properties.setAuthBodyLimit(DataSize.ofBytes(64));
        properties.setPaymentCallbackBodyLimit(DataSize.ofBytes(512));
        return properties;
    }

    private static GatewayRateLimitProperties rateProperties() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties();
        properties.setHmacSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        properties.setOrdinary(new GatewayRateLimitProperties.Bucket(
                20, Duration.ofMinutes(5), 20));
        return properties;
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory connectionFactory(
            GenericContainer<?> container) {
        var configuration = new org.springframework.data.redis.connection.RedisStandaloneConfiguration(
                container.getHost(), container.getMappedPort(6379));
        var factory = new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    private static String environment() {
        return "it-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private static boolean secretEquals(String actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private record GatewayRuntime(
            DisposableServer server,
            WebTestClient client,
            HttpClient safeHttpClient) implements AutoCloseable {

        SafeHttpResponse authorizedGet(
                String path, String bearerToken, Map<String, String> additionalHeaders) {
            return safeHttpClient.headers(headers -> {
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
                        additionalHeaders.forEach(headers::set);
                    })
                    .get()
                    .uri(path)
                    .responseSingle((response, body) -> body.asString()
                            .defaultIfEmpty("")
                            .map(content -> new SafeHttpResponse(
                                    response.status().code(),
                                    content,
                                    responseHeaders(response.responseHeaders()))))
                    .block(Duration.ofSeconds(5));
        }

        private static Map<String, String> responseHeaders(io.netty.handler.codec.http.HttpHeaders headers) {
            Map<String, String> selected = new LinkedHashMap<>();
            for (String name : List.of(
                    "X-Request-Id",
                    "X-Content-Type-Options",
                    HttpHeaders.CONTENT_TYPE,
                    HttpHeaders.RETRY_AFTER)) {
                String value = headers.get(name);
                if (value != null) {
                    selected.put(name, value);
                }
            }
            return Map.copyOf(selected);
        }

        @Override
        public void close() {
            server.disposeNow();
        }
    }

    private record SafeHttpResponse(int status, String body, Map<String, String> headers) {
    }
}
