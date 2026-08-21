package com.educloud.gateway.integration;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.educloud.gateway.GatewayApplication;
import com.educloud.gateway.security.TestJwtKeys;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NacosGatewayRoutingIT {

    private static final DockerImageName NACOS_IMAGE = TestContainerImages.nacos();
    private static final DockerImageName REDIS_IMAGE = TestContainerImages.redis();
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(5);
    private static final String CONFIG_GROUP = "EDUCLOUD_GATEWAY";
    private static final String DISCOVERY_GROUP = "EDUCLOUD_SERVICES";
    private static final String CONFIG_DATA_ID = "educloud-gateway.yaml";
    private static final String GATEWAY_SERVICE = "educloud-gateway";
    private static final String ISSUER = "https://issuer.educloud.local";
    private static final String AUDIENCE = "educloud-api";
    private static final String SUBJECT = "nacos-routing-user";
    private static final String SESSION_ID = "nacos-routing-session";
    private static final long TOKEN_VERSION = 11L;
    private static final List<String> SERVICES = List.of(
            "educloud-user",
            "educloud-course",
            "educloud-content",
            "educloud-order",
            "educloud-payment",
            "educloud-live",
            "educloud-file",
            "educloud-notification",
            "educloud-analytics",
            "educloud-search",
            "educloud-recommendation");

    private final String resourceId = UUID.randomUUID().toString();
    private final String namespace = "it-" + resourceId;
    private final String environment = "it-" + resourceId.replace("-", "").substring(0, 20);
    private final String gatewayUsername = "gateway_" + resourceId.replace("-", "").substring(0, 16);
    private final String gatewayPassword = "Gw" + resourceId.replace("-", "") + "9x";
    private final TestJwtKeys signingKeys = new TestJwtKeys();
    private final IntegrationResourceTracker resources = new IntegrationResourceTracker();
    private final Map<String, DisposableServer> downstreams = new LinkedHashMap<>();

    private FixedPortNacosContainer nacos;
    private GenericContainer<?> redisContainer;
    private org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory redisFactory;
    private org.springframework.data.redis.core.ReactiveStringRedisTemplate redis;
    private NacosAdminClient admin;
    private NamingService adminNaming;
    private NamingService gatewayNaming;
    private ConfigService gatewayConfig;
    private ConfigurableApplicationContext gatewayContext;
    private int gatewayPort;
    private HttpClient safeGatewayClient;
    private String token;

    @BeforeAll
    void provisionIsolatedNacosRedisAndGateway() throws Exception {
        try {
            redisContainer = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
            redisContainer.start();
            redisFactory = redisConnectionFactory(redisContainer);
            redis = new org.springframework.data.redis.core.ReactiveStringRedisTemplate(redisFactory);

            PortPair ports = PortPair.reserve();
            nacos = new FixedPortNacosContainer(ports)
                    .withEnv("MODE", "standalone")
                    .withEnv("NACOS_AUTH_ENABLE", "true")
                    .withEnv("NACOS_AUTH_TOKEN", randomBase64(32))
                    .withEnv("NACOS_AUTH_IDENTITY_KEY", "it-key-" + resourceId)
                    .withEnv("NACOS_AUTH_IDENTITY_VALUE", "it-value-" + resourceId)
                    .withEnv("JVM_XMS", "256m")
                    .withEnv("JVM_XMX", "256m")
                    .withEnv("JVM_XMN", "128m")
                    .waitingFor(Wait.forHttp("/nacos/v1/console/health/readiness")
                            .forPort(8848)
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(2)));
            nacos.start();

            String serverAddr = nacos.getHost() + ":" + ports.http();
            admin = new NacosAdminClient("http://" + serverAddr + "/nacos", new ObjectMapper());
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                    assertThat(admin.login("nacos", "nacos")).isNotBlank());
            admin.useToken(admin.login("nacos", "nacos"));

            admin.createNamespace(namespace, resourceId);
            resources.track("namespace:" + resourceId, () ->
                    admin.requireSuccess("DELETE", "/v1/console/namespaces", Map.of("namespaceId", namespace)));
            admin.createUser(gatewayUsername, gatewayPassword);
            resources.track("user:" + resourceId, () ->
                    admin.requireSuccess("DELETE", "/v1/auth/users", Map.of("username", gatewayUsername)));
            admin.addRole(gatewayUsername, gatewayUsername);
            resources.track("role:" + resourceId, () -> admin.requireSuccess(
                    "DELETE", "/v1/auth/roles",
                    Map.of("role", gatewayUsername, "username", gatewayUsername)));

            addPermission("r", NacosPermissionResources.config(
                    namespace, CONFIG_GROUP, CONFIG_DATA_ID));
            addPermission("r", NacosPermissionResources.naming(
                    namespace, DISCOVERY_GROUP, GATEWAY_SERVICE));
            addPermission("w", NacosPermissionResources.naming(
                    namespace, DISCOVERY_GROUP, GATEWAY_SERVICE));
            for (String service : SERVICES) {
                addPermission("r", NacosPermissionResources.naming(
                        namespace, DISCOVERY_GROUP, service));
            }

            String configContent = "educloud:\n  gateway:\n    integration-marker: " + resourceId + "\n";
            admin.publishConfig(namespace, CONFIG_GROUP, CONFIG_DATA_ID, configContent);
            resources.track("config:" + resourceId, () ->
                    admin.deleteConfig(namespace, CONFIG_GROUP, CONFIG_DATA_ID));

            adminNaming = NacosFactory.createNamingService(nacosProperties(
                    serverAddr, namespace, "nacos", "nacos"));
            resources.track("admin-naming-client:" + resourceId, adminNaming::shutDown);
            for (String service : SERVICES) {
                DisposableServer downstream = startDownstream(service);
                downstreams.put(service, downstream);
                resources.track("downstream:" + service + ":" + resourceId, downstream::disposeNow);
                register(service, downstream.port());
                resources.track("instance:" + service + ":" + resourceId,
                        () -> deregister(service, downstream.port()));
            }

            gatewayConfig = NacosFactory.createConfigService(nacosProperties(
                    serverAddr, namespace, gatewayUsername, gatewayPassword));
            resources.track("gateway-config-client:" + resourceId, gatewayConfig::shutDown);
            gatewayNaming = NacosFactory.createNamingService(nacosProperties(
                    serverAddr, namespace, gatewayUsername, gatewayPassword));
            resources.track("gateway-naming-client:" + resourceId, gatewayNaming::shutDown);
            assertThat(gatewayConfig.getConfig(CONFIG_DATA_ID, CONFIG_GROUP, 5_000))
                    .contains(resourceId);

            gatewayContext = startGatewayContext(serverAddr);
            resources.track("gateway-context:" + resourceId, gatewayContext::close);
            gatewayPort = ((ReactiveWebServerApplicationContext) gatewayContext).getWebServer().getPort();
            safeGatewayClient = HttpClient.create()
                    .baseUrl("http://127.0.0.1:" + gatewayPort)
                    .responseTimeout(Duration.ofSeconds(10));
            writeActiveSession();
            token = signingKeys.signedToken(validClaims());

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(
                    adminNaming.selectInstances(GATEWAY_SERVICE, DISCOVERY_GROUP, true))
                    .anyMatch(instance -> instance.getPort() == gatewayPort && instance.isHealthy()));
        } catch (Throwable failure) {
            shutdownInfrastructure();
            throw failure;
        }
    }

    @AfterAll
    void cleanupAllResources() {
        shutdownInfrastructure();
    }

    @Test
    @Order(1)
    void routesAllSeventeenStaticHttpAndWebSocketRoutesThroughNacos() {
        Map<String, String> routes = new LinkedHashMap<>();
        routes.put("/api/v1/users/100", "educloud-user");
        routes.put("/api/v1/me", "educloud-user");
        routes.put("/api/v1/me/assignments", "educloud-content");
        routes.put("/api/v1/me/enrollments", "educloud-course");
        routes.put("/api/v1/courses/c1/chapters/one", "educloud-content");
        routes.put("/api/v1/chapters/one", "educloud-content");
        routes.put("/api/v1/teacher/courses/c1/content-draft", "educloud-content");
        routes.put("/api/v1/courses/c1", "educloud-course");
        routes.put("/api/v1/orders/one", "educloud-order");
        routes.put("/api/v1/payments/one", "educloud-payment");
        routes.put("/api/v1/live-rooms/one", "educloud-live");
        routes.put("/api/v1/files/one", "educloud-file");
        routes.put("/api/v1/notifications/one", "educloud-notification");
        routes.put("/api/v1/analytics/overview", "educloud-analytics");
        routes.put("/api/v1/search/query", "educloud-search");
        routes.put("/api/v1/recommendations/one", "educloud-recommendation");

        routes.forEach((path, service) -> {
            SafeHttpResponse response = authorizedGet(path);
            assertThat(response.status()).as("route status for " + path).isEqualTo(200);
            assertThat(response.downstreamService()).as("route target for " + path).isEqualTo(service);
            assertThat(response.body()).as("route body for " + path).isEqualTo(service);
        });

        AtomicReference<String> websocketReply = new AtomicReference<>();
        HttpClient.create()
                .headers(headers -> headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .websocket()
                .uri("ws://127.0.0.1:" + gatewayPort + "/ws/v1/live/room-one")
                .handle((inbound, outbound) -> outbound.sendString(Mono.just("ping"))
                        .then(inbound.receive().asString().next()
                                .doOnNext(websocketReply::set)
                                .then()))
                .then()
                .block(Duration.ofSeconds(10));
        assertThat(websocketReply).hasValue("live:ping");
    }

    @Test
    @Order(2)
    void disablesAutomaticLocatorAndReturns503ForKnownRouteWithoutInstances() throws Exception {
        assertThat(authorizedGet("/educloud-user/api/v1/users/100").status()).isEqualTo(404);

        DisposableServer recommendation = downstreams.get("educloud-recommendation");
        deregister("educloud-recommendation", recommendation.port());
        try {
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
                    adminNaming.selectInstances("educloud-recommendation", DISCOVERY_GROUP, true)).isEmpty());
            assertThat(authorizedGet("/api/v1/recommendations/one").status()).isEqualTo(503);
        } finally {
            register("educloud-recommendation", recommendation.port());
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
                    adminNaming.selectInstances("educloud-recommendation", DISCOVERY_GROUP, true))
                    .isNotEmpty());
        }
    }

    @Test
    @Order(3)
    void gatewayIdentityCanReadOnlyItsConfigAndApprovedDiscoveryResources() throws Exception {
        assertThat(gatewayConfig.getConfig(CONFIG_DATA_ID, CONFIG_GROUP, 5_000)).contains(resourceId);
        assertThat(gatewayNaming.selectInstances("educloud-user", DISCOVERY_GROUP, true)).isNotEmpty();

        boolean configWriteRejected;
        try {
            configWriteRejected = !gatewayConfig.publishConfig(
                    CONFIG_DATA_ID, CONFIG_GROUP, "unauthorized=true");
        } catch (Exception expected) {
            configWriteRejected = true;
        }
        assertThat(configWriteRejected).as("Gateway config write permission").isTrue();
        assertThatThrownBy(() -> gatewayNaming.selectInstances(
                "educloud-unrelated", "UNRELATED_GROUP", true))
                .isInstanceOf(Exception.class);
    }

    private ConfigurableApplicationContext startGatewayContext(String serverAddr) {
        Map<String, String> properties = new HashMap<>();
        properties.put("server.port", "0");
        properties.put("management.server.address", "127.0.0.1");
        properties.put("management.server.port", "0");
        properties.put("spring.main.banner-mode", "off");
        properties.put("spring.cloud.gateway.discovery.locator.enabled", "false");
        properties.put("spring.cloud.nacos.server-addr", serverAddr);
        properties.put("spring.cloud.nacos.username", gatewayUsername);
        properties.put("spring.cloud.nacos.password", gatewayPassword);
        properties.put("spring.cloud.nacos.config.server-addr", serverAddr);
        properties.put("spring.cloud.nacos.config.namespace", namespace);
        properties.put("spring.cloud.nacos.config.group", CONFIG_GROUP);
        properties.put("spring.cloud.nacos.config.username", gatewayUsername);
        properties.put("spring.cloud.nacos.config.password", gatewayPassword);
        properties.put("spring.cloud.nacos.discovery.server-addr", serverAddr);
        properties.put("spring.cloud.nacos.discovery.namespace", namespace);
        properties.put("spring.cloud.nacos.discovery.group", DISCOVERY_GROUP);
        properties.put("spring.cloud.nacos.discovery.username", gatewayUsername);
        properties.put("spring.cloud.nacos.discovery.password", gatewayPassword);
        properties.put("spring.cloud.nacos.discovery.ip", "127.0.0.1");
        properties.put("educloud.gateway.environment", environment);
        properties.put("educloud.gateway.security.jwks-json", signingKeys.publicJwksJson());
        properties.put("educloud.gateway.security.issuer", ISSUER);
        properties.put("educloud.gateway.security.audience", AUDIENCE);
        properties.put("educloud.gateway.ratelimit.hmac-secret-base64", randomBase64(32));
        properties.put("educloud.gateway.web.allowed-origins[0]", "http://localhost:5173");
        properties.put("educloud.gateway.nacos.server-addr", serverAddr);
        properties.put("educloud.gateway.nacos.namespace", namespace);
        properties.put("educloud.gateway.nacos.config-group", CONFIG_GROUP);
        properties.put("educloud.gateway.nacos.discovery-group", DISCOVERY_GROUP);
        properties.put("educloud.gateway.nacos.username", gatewayUsername);
        properties.put("educloud.gateway.nacos.password", gatewayPassword);
        properties.put("spring.data.redis.host", redisContainer.getHost());
        properties.put("spring.data.redis.port", redisContainer.getMappedPort(6379).toString());
        properties.put("management.tracing.sampling.probability", "0.0");
        return new SpringApplicationBuilder(GatewayApplication.class)
                .run(GatewayIntegrationTestProperties.asArguments(properties));
    }

    private SafeHttpResponse authorizedGet(String path) {
        return safeGatewayClient
                .headers(headers -> headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .get()
                .uri(path)
                .responseSingle((response, body) -> body.asString()
                        .defaultIfEmpty("")
                        .map(content -> new SafeHttpResponse(
                                response.status().code(),
                                content,
                                response.responseHeaders().get("X-Downstream-Service"))))
                .block(Duration.ofSeconds(10));
    }

    private DisposableServer startDownstream(String service) {
        HttpServer server = HttpServer.create().host("127.0.0.1").port(0);
        if ("educloud-live".equals(service)) {
            return server.route(routes -> routes
                            .ws("/ws/v1/live/room-one", (inbound, outbound) ->
                                    outbound.sendString(inbound.receive().asString().map(value -> "live:" + value)))
                            .route(request -> true, (request, response) -> response
                                    .header("X-Downstream-Service", service)
                                    .sendString(Mono.just(service))))
                    .bindNow();
        }
        return server.handle((request, response) -> response
                        .header("X-Downstream-Service", service)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                        .sendString(Mono.just(service)))
                .bindNow();
    }

    private void register(String service, int port) throws Exception {
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(port);
        instance.setHealthy(true);
        instance.setEnabled(true);
        instance.setEphemeral(true);
        instance.setClusterName("DEFAULT");
        adminNaming.registerInstance(service, DISCOVERY_GROUP, instance);
    }

    private void deregister(String service, int port) throws Exception {
        adminNaming.deregisterInstance(service, DISCOVERY_GROUP, "127.0.0.1", port, "DEFAULT");
    }

    private void addPermission(String action, String resource) throws Exception {
        admin.addPermission(gatewayUsername, resource, action);
        resources.track("permission:" + action + ":" + resourceId, () -> admin.requireSuccess(
                "DELETE", "/v1/auth/permissions",
                Map.of("role", gatewayUsername, "resource", resource, "action", action)));
    }

    private void writeActiveSession() {
        String key = "educloud:{" + environment + ":auth}:session:" + SESSION_ID;
        redis.opsForHash().putAll(key, Map.of(
                "subject", SUBJECT,
                "status", "ACTIVE",
                "tokenVersion", Long.toString(TOKEN_VERSION))).block(BLOCK_TIMEOUT);
        assertThat(redis.expire(key, Duration.ofMinutes(10)).block(BLOCK_TIMEOUT)).isTrue();
        resources.track("redis-keys:" + resourceId, () -> {
            List<String> keys = redis.scan(org.springframework.data.redis.core.ScanOptions.scanOptions()
                            .match("educloud:{" + environment + ":*")
                            .count(200)
                            .build())
                    .collectList().block(BLOCK_TIMEOUT);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(Flux.fromIterable(keys)).block(BLOCK_TIMEOUT);
            }
            assertThat(redis.scan(org.springframework.data.redis.core.ScanOptions.scanOptions()
                            .match("educloud:{" + environment + ":*").build())
                    .collectList().block(BLOCK_TIMEOUT)).isEmpty();
        });
    }

    private Map<String, Object> validClaims() {
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
        claims.put("exp", now.plusSeconds(600));
        return claims;
    }

    private Properties nacosProperties(
            String serverAddr, String targetNamespace, String username, String password) {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, serverAddr);
        properties.setProperty(PropertyKeyConst.NAMESPACE, targetNamespace);
        properties.setProperty(PropertyKeyConst.USERNAME, username);
        properties.setProperty(PropertyKeyConst.PASSWORD, password);
        return properties;
    }

    private void shutdownInfrastructure() {
        AssertionError cleanupFailure = null;
        try {
            resources.close();
        } catch (AssertionError failure) {
            cleanupFailure = failure;
        } finally {
            if (redisFactory != null) {
                redisFactory.destroy();
            }
            if (redisContainer != null) {
                redisContainer.stop();
            }
            if (nacos != null) {
                nacos.stop();
            }
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    private static org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory redisConnectionFactory(
            GenericContainer<?> container) {
        var configuration = new org.springframework.data.redis.connection.RedisStandaloneConfiguration(
                container.getHost(), container.getMappedPort(6379));
        var factory = new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    private static String randomBase64(int bytes) {
        byte[] value = new byte[bytes];
        new SecureRandom().nextBytes(value);
        return Base64.getEncoder().encodeToString(value);
    }

    private record PortPair(int http, int grpc) {
        static PortPair reserve() throws IOException {
            for (int attempt = 0; attempt < 100; attempt++) {
                try (ServerSocket httpSocket = new ServerSocket(0)) {
                    int http = httpSocket.getLocalPort();
                    int grpc = http + 1_000;
                    if (grpc > 65_535) {
                        continue;
                    }
                    try (ServerSocket ignored = new ServerSocket(grpc)) {
                        return new PortPair(http, grpc);
                    } catch (IOException ignored) {
                        // Try another adjacent host port pair.
                    }
                }
            }
            throw new IOException("unable to reserve adjacent Nacos HTTP and gRPC ports");
        }
    }

    private record SafeHttpResponse(int status, String body, String downstreamService) {
    }

    private static final class FixedPortNacosContainer extends GenericContainer<FixedPortNacosContainer> {
        private FixedPortNacosContainer(PortPair ports) {
            super(NACOS_IMAGE);
            addFixedExposedPort(ports.http(), 8848);
            addFixedExposedPort(ports.grpc(), 9848);
        }
    }

    private static final class NacosAdminClient {

        private final String baseUrl;
        private final ObjectMapper objectMapper;
        private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        private String token;

        private NacosAdminClient(String baseUrl, ObjectMapper objectMapper) {
            this.baseUrl = baseUrl;
            this.objectMapper = objectMapper;
        }

        String login(String username, String password) throws Exception {
            HttpResponse<String> response = request("POST", "/v1/auth/login", Map.of(
                    "username", username,
                    "password", password), false);
            requireHttpSuccess(response, "Nacos login");
            JsonNode root = objectMapper.readTree(response.body());
            String accessToken = root.path("accessToken").asText("");
            if (accessToken.isBlank()) {
                throw new IllegalStateException("Nacos login returned no access token");
            }
            return accessToken;
        }

        void useToken(String accessToken) {
            token = accessToken;
        }

        void createNamespace(String namespace, String marker) throws Exception {
            requireSuccess("POST", "/v1/console/namespaces", Map.of(
                    "customNamespaceId", namespace,
                    "namespaceName", namespace,
                    "namespaceDesc", "Gateway IT " + marker));
        }

        void createUser(String username, String password) throws Exception {
            requireSuccess("POST", "/v1/auth/users", Map.of(
                    "username", username,
                    "password", password));
        }

        void addRole(String role, String username) throws Exception {
            requireSuccess("POST", "/v1/auth/roles", Map.of(
                    "role", role,
                    "username", username));
        }

        void addPermission(String role, String resource, String action) throws Exception {
            requireSuccess("POST", "/v1/auth/permissions", Map.of(
                    "role", role,
                    "resource", resource,
                    "action", action));
        }

        void publishConfig(String namespace, String group, String dataId, String content) throws Exception {
            HttpResponse<String> response = request("POST", "/v1/cs/configs", Map.of(
                    "tenant", namespace,
                    "group", group,
                    "dataId", dataId,
                    "content", content), true);
            requireHttpSuccess(response, "publish Nacos config");
            if (!"true".equalsIgnoreCase(response.body().trim())) {
                throw new IllegalStateException("Nacos config publish was not acknowledged");
            }
        }

        void deleteConfig(String namespace, String group, String dataId) throws Exception {
            HttpResponse<String> response = request("DELETE", "/v1/cs/configs", Map.of(
                    "tenant", namespace,
                    "group", group,
                    "dataId", dataId), true);
            requireHttpSuccess(response, "delete Nacos config");
            if (!"true".equalsIgnoreCase(response.body().trim())) {
                throw new IllegalStateException("Nacos config deletion was not acknowledged");
            }
        }

        void requireSuccess(String method, String path, Map<String, String> parameters) throws Exception {
            HttpResponse<String> response = request(method, path, parameters, true);
            requireHttpSuccess(response, "Nacos mutation " + method + " " + path);
            String body = response.body().trim();
            if (body.startsWith("{")) {
                JsonNode root = objectMapper.readTree(body);
                if (root.has("code") && root.path("code").asInt(500) >= 400) {
                    throw new IllegalStateException("Nacos mutation returned an application error");
                }
            }
        }

        private HttpResponse<String> request(
                String method, String path, Map<String, String> parameters, boolean authenticated)
                throws IOException, InterruptedException {
            Map<String, String> form = new LinkedHashMap<>(parameters);
            if (authenticated) {
                if (token == null || token.isBlank()) {
                    throw new IllegalStateException("Nacos administrator token is unavailable");
                }
                form.put("accessToken", token);
            }
            String encoded = encode(form);
            HttpRequest.Builder builder;
            if ("POST".equals(method)) {
                builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString(encoded));
            } else {
                builder = HttpRequest.newBuilder(URI.create(baseUrl + path + "?" + encoded))
                        .method(method, HttpRequest.BodyPublishers.noBody());
            }
            return httpClient.send(builder.timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        private static void requireHttpSuccess(HttpResponse<String> response, String operation) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(operation + " failed with HTTP " + response.statusCode());
            }
        }

        private static String encode(Map<String, String> form) {
            List<String> fields = new ArrayList<>(form.size());
            form.forEach((name, value) -> fields.add(
                    URLEncoder.encode(name, StandardCharsets.UTF_8)
                            + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)));
            return String.join("&", fields);
        }
    }
}
