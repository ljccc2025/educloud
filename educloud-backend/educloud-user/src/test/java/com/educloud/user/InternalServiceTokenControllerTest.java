package com.educloud.user;

import com.educloud.user.entity.ServiceClientCredentialEntity;
import com.educloud.user.entity.ServiceClientEntity;
import com.educloud.user.mapper.ServiceClientCredentialMapper;
import com.educloud.user.mapper.ServiceClientMapper;
import com.educloud.user.security.JwtKeyProvider;
import com.educloud.user.session.SessionFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内部服务令牌签发端点（POST /internal/v1/service-tokens）集成测试。
 * 真实 SecurityFilterChain + InternalApiFilter + ServiceTokenService（mocked 持久层）+
 * 测试密钥签发；返回的 access_token 用公钥解码校验 aud/scope/expires_in。
 * 依据：M03 设计规格第 8 节（HTTP Basic client_credentials；凭据无效 401 且不发业务错误码）。
 */
class InternalServiceTokenControllerTest {

    private static final String CLIENT_ID = "educloud-course";
    private static final String SECRET = "course-secret";
    private static final String AUDIENCE = "educloud-file";
    private static final String SCOPE = "file:internal";

    private static ConfigurableApplicationContext context;
    private static MockMvc mockMvc;
    private static ServiceClientMapper clientMapper;
    private static ServiceClientCredentialMapper credentialMapper;
    private static RSAPublicKey publicKey;

    @BeforeAll
    static void startContext() throws Exception {
        String keyFile = writePkcs8Key();
        context = new SpringApplicationBuilder(UserApplication.class, MethodSecurityAndAdminEndpointsTest.MockServices.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        // 管理端口同样随机，避免与宿主机运行中的服务端口冲突
                        "--management.server.port=0",
                        "--spring.main.banner-mode=off",
                        "--spring.autoconfigure.exclude="
                                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,"
                                + "com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration,"
                                + "org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClientAutoConfiguration",
                        "--spring.cloud.nacos.discovery.enabled=false",
                        "--spring.cloud.nacos.config.enabled=false",
                        "--spring.cloud.nacos.discovery.register-enabled=false",
                        "--management.tracing.sampling.probability=0.0",
                        "--management.endpoint.health.group.readiness.include=readinessState",
                        "--educloud.user.session.environment=test",
                        "--educloud.user.jwt.private-key-location=" + keyFile,
                        "--educloud.user.jwt.issuer=https://issuer.educloud.local",
                        "--educloud.user.jwt.audience=educloud-api");
        mockMvc = MockMvcBuilders.webAppContextSetup(
                (org.springframework.web.context.WebApplicationContext) context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        clientMapper = context.getBean(ServiceClientMapper.class);
        credentialMapper = context.getBean(ServiceClientCredentialMapper.class);
        publicKey = ((RSAKey) context.getBean(JwtKeyProvider.class).publicJwkSet().getKeys().get(0))
                .toRSAPublicKey();
    }

    @AfterAll
    static void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    private static void stubValidCredentials() {
        when(clientMapper.selectOne(any())).thenReturn(client());
        when(credentialMapper.selectOne(any())).thenReturn(credential(SessionFactory.sha256Hex(SECRET)));
    }

    @Test
    void issuesDecodableServiceTokenForValidBasicCredentials() throws Exception {
        stubValidCredentials();

        String body = mockMvc.perform(post("/internal/v1/service-tokens")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth(CLIENT_ID, SECRET))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("audience", AUDIENCE)
                        .param("scope", SCOPE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(300))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String accessToken = new ObjectMapper().readTree(body).get("access_token").asText();
        assertThat(accessToken).isNotBlank();
        Jwt decoded = NimbusJwtDecoder.withPublicKey(publicKey).build().decode(accessToken);
        assertThat(decoded.getSubject()).isEqualTo("service:" + CLIENT_ID);
        assertThat(decoded.getAudience()).containsExactly(AUDIENCE);
        assertThat(decoded.getClaimAsStringList("scope")).containsExactly(SCOPE);
        assertThat((String) decoded.getClaim("clientId")).isEqualTo(CLIENT_ID);
        assertThat(((Number) decoded.getClaim("tokenVersion")).longValue()).isEqualTo(1L);
        assertThat(Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt()).getSeconds())
                .isEqualTo(300L);
    }

    @Test
    void issuesTokenFromJsonBody() throws Exception {
        stubValidCredentials();

        mockMvc.perform(post("/internal/v1/service-tokens")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth(CLIENT_ID, SECRET))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grant_type\":\"client_credentials\",\"audience\":\"educloud-file\",\"scope\":\"file:internal\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(300))
                .andExpect(jsonPath("$.access_token").isString());
    }

    @Test
    void rejectsMissingAuthorizationHeaderWith401() throws Exception {
        stubValidCredentials();

        mockMvc.perform(post("/internal/v1/service-tokens")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("audience", AUDIENCE)
                        .param("scope", SCOPE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongSecretWith401AndNoBusinessErrorCode() throws Exception {
        stubValidCredentials();
        when(credentialMapper.selectOne(any()))
                .thenReturn(credential(SessionFactory.sha256Hex("other-secret")));

        MvcResult result = mockMvc.perform(post("/internal/v1/service-tokens")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth(CLIENT_ID, "wrong-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("audience", AUDIENCE)
                        .param("scope", SCOPE))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("SERVICE_");
    }

    @Test
    void rejectsUnknownClientWith401() throws Exception {
        when(clientMapper.selectOne(any())).thenReturn(null);

        mockMvc.perform(post("/internal/v1/service-tokens")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("ghost-service", "x"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("audience", AUDIENCE)
                        .param("scope", SCOPE))
                .andExpect(status().isUnauthorized());
    }

    private static ServiceClientEntity client() {
        ServiceClientEntity client = new ServiceClientEntity();
        client.setId(1L);
        client.setClientId(CLIENT_ID);
        client.setStatus("ACTIVE");
        client.setTokenVersion(1L);
        client.setAllowedAudiencesJson("[\"educloud-file\"]");
        client.setAllowedScopesJson("[\"file:internal\"]");
        return client;
    }

    private static ServiceClientCredentialEntity credential(String secretHash) {
        ServiceClientCredentialEntity credential = new ServiceClientCredentialEntity();
        credential.setServiceClientId(1L);
        credential.setSecretHash(secretHash);
        credential.setStatus("ACTIVE");
        return credential;
    }

    private static String basicAuth(String clientId, String secret) {
        String raw = clientId + ":" + secret;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String writePkcs8Key() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) pair.getPrivate();
        String base64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + wrap(base64) + "-----END PRIVATE KEY-----\n";
        Path keyFile = Files.createTempFile("service-token-controller-", ".pem");
        Files.write(keyFile, pem.getBytes(StandardCharsets.US_ASCII));
        return keyFile.toString();
    }

    private static String wrap(String base64) {
        StringBuilder wrapped = new StringBuilder();
        for (int index = 0; index < base64.length(); index += 64) {
            wrapped.append(base64, index, Math.min(index + 64, base64.length())).append('\n');
        }
        return wrapped.toString();
    }
}
