package com.educloud.course.security;

import com.educloud.course.CourseApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M05 任务 6：Resource Server 安全链集成测试（真实 JWKS 验签 + 方法安全）。
 *
 * <p>测试 profile（application-test.yml）排除全部外部中间件；测试内生成临时 RSA
 * 密钥对，JWKS 文件经 @DynamicPropertySource 注入 {@code educloud.course.jwt.jwks-location}
 * （与 educloud-file SecurityConfigurationTest 同法），私钥签发用户令牌。断言：无 token
 * 401 信封；course:audit 权限放行受保护端点；无该权限 403 COURSE_ACCESS_DENIED；
 * 错误 aud 401；非信任密钥（验签失败）401；过期 token 401。（actuator 按
 * application-test.yml 运行在独立 management 端口，不在主 SecurityFilterChain 作用域内，
 * 故不在此断言匿名可达。）</p>
 */
@SpringBootTest(classes = CourseApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    private static final String ISSUER = "https://issuer.educloud.local";
    private static final TestJwtKeys TEST_KEYS = new TestJwtKeys();

    @Autowired
    MockMvc mockMvc;

    @DynamicPropertySource
    static void jwksLocation(DynamicPropertyRegistry registry) throws Exception {
        Path jwksFile = Files.createTempFile("course-security-jwks-", ".json");
        Files.writeString(jwksFile, TEST_KEYS.publicJwksJson());
        registry.add("educloud.course.jwt.jwks-location", () -> "file:" + jwksFile.toAbsolutePath());
    }

    @Test
    void rejectsMissingTokenWith401Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/courses/security-probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void allowsTokenWithAuditPermission() throws Exception {
        mockMvc.perform(get("/api/v1/courses/security-probe")
                        .header("Authorization", "Bearer " + userToken(List.of("course:audit"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("probe-ok"));
    }

    @Test
    void rejectsTokenWithoutAuditPermission() throws Exception {
        mockMvc.perform(get("/api/v1/courses/security-probe")
                        .header("Authorization", "Bearer " + userToken(List.of("course:create"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_ACCESS_DENIED"));
    }

    @Test
    void rejectsTokenWithWrongAudience() throws Exception {
        mockMvc.perform(get("/api/v1/courses/security-probe")
                        .header("Authorization", "Bearer " + userToken("educloud-user", List.of("course:audit"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void rejectsTokenSignedByUntrustedKey() throws Exception {
        // 相同 claims、不同密钥对签发：kid 未知/验签失败 → 401。
        TestJwtKeys untrusted = new TestJwtKeys();
        mockMvc.perform(get("/api/v1/courses/security-probe")
                        .header("Authorization", "Bearer " + untrusted.signedToken(
                                userClaims("educloud-api", List.of("course:audit"), Instant.now()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        Instant issued = Instant.now().minusSeconds(300);
        Map<String, Object> claims = userClaims("educloud-api", List.of("course:audit"), issued);
        claims.put("exp", issued.plusSeconds(120)); // 已过期 180s，超出 30s 时钟容差
        mockMvc.perform(get("/api/v1/courses/security-probe")
                        .header("Authorization", "Bearer " + TEST_KEYS.signedToken(claims)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private static String userToken(List<String> permissions) {
        return userToken("educloud-api", permissions);
    }

    private static String userToken(String audience, List<String> permissions) {
        return TEST_KEYS.signedToken(userClaims(audience, permissions, Instant.now()));
    }

    private static Map<String, Object> userClaims(String audience, List<String> permissions, Instant now) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", ISSUER);
        claims.put("aud", List.of(audience));
        claims.put("exp", now.plusSeconds(300));
        claims.put("nbf", now.minusSeconds(1));
        claims.put("iat", now.minusSeconds(1));
        claims.put("sub", "1001");
        claims.put("sid", "session-1001");
        claims.put("tokenVersion", 1L);
        claims.put("userType", "STUDENT");
        claims.put("roles", List.of("STUDENT"));
        claims.put("permissions", permissions);
        return claims;
    }
}
