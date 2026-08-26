package com.educloud.search.security;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.search.config.ElasticsearchProperties;
import com.educloud.search.config.SearchProperties;
import com.educloud.search.config.SecurityConfig;
import com.educloud.search.controller.SearchAdminController;
import com.educloud.search.controller.SearchController;
import com.educloud.search.dto.response.CourseSearchResponse;
import com.educloud.search.dto.response.IndexTaskProgressResponse;
import com.educloud.search.dto.response.SuggestResponse;
import com.educloud.search.service.IndexRebuildService;
import com.educloud.search.service.SearchService;
import com.educloud.search.service.SuggestService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Search 服务端点安全与权限访问控制测试（JWKS 真实验签 + 白名单放行 + 方法级安全）
 */
@WebMvcTest(controllers = {SearchController.class, SearchAdminController.class})
@Import({
        SecurityConfig.class,
        JwtDecoderConfiguration.class,
        InternalApiFilter.class,
        SecurityAccessTest.TestInfrastructure.class
})
class SecurityAccessTest {

    private static final String ISSUER = "educloud-auth";
    private static final String AUDIENCE = "educloud-web";
    private static final TestJwtKeys TEST_KEYS = new TestJwtKeys();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    @MockBean
    private SuggestService suggestService;

    @MockBean
    private IndexRebuildService indexRebuildService;

    @org.junit.jupiter.api.BeforeEach
    @org.junit.jupiter.api.AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) throws Exception {
        Path jwksFile = Files.createTempFile("search-security-jwks-", ".json");
        Files.writeString(jwksFile, TEST_KEYS.publicJwksJson());
        registry.add("educloud.search.jwt.jwks-location", () -> "file:" + jwksFile.toAbsolutePath());
        registry.add("educloud.search.jwt.issuer", () -> ISSUER);
        registry.add("educloud.search.jwt.audience", () -> AUDIENCE);
        registry.add("educloud.search.internal.secret-token", () -> "educloud-internal-secret");
    }

    @Test
    @DisplayName("白名单测试：匿名访问 /api/v1/search/courses 无需 Token 直接放行 (200)")
    void testWhitelistCoursesAnonymousPass() throws Exception {
        when(searchService.searchCourses(any())).thenReturn(CourseSearchResponse.empty(1, 20));

        mockMvc.perform(get("/api/v1/search/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("白名单测试：匿名访问 /api/v1/search/suggest 无需 Token 直接放行 (200)")
    void testWhitelistSuggestAnonymousPass() throws Exception {
        when(suggestService.suggest(any(), any())).thenReturn(SuggestResponse.empty());

        mockMvc.perform(get("/api/v1/search/suggest").param("q", "Java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("受保护端点：未登录（缺失 Authorization Token）访问管理端报 401 UNAUTHENTICATED")
    void testUnauthenticatedAccessToAdminReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/search/admin/rebuild-index"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    @DisplayName("方法级权限：已登录但无 search:rebuild 或 ADMIN 权限访问管理端报 403 ACCESS_DENIED")
    void testForbiddenAccessToAdminReturns403() throws Exception {
        String token = userToken("student_user", List.of("course:read"), List.of("STUDENT"));

        mockMvc.perform(post("/api/v1/search/admin/rebuild-index")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    @DisplayName("权限放行：拥有 search:rebuild 权限的用户访问管理端正常放行 (200)")
    void testAuthorizedUserWithSearchRebuildPermissionPass() throws Exception {
        when(indexRebuildService.triggerFullRebuild(any())).thenReturn(
                IndexTaskProgressResponse.builder().taskNo("SR_001").createdBy("admin_op").build()
        );

        String token = userToken("admin_op", List.of("search:rebuild"), List.of("OPERATOR"));

        mockMvc.perform(post("/api/v1/search/admin/rebuild-index")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.taskNo").value("SR_001"));
    }

    @Test
    @DisplayName("权限放行：拥有 ROLE_ADMIN / ADMIN 角色的用户访问管理端正常放行 (200)")
    void testAuthorizedUserWithAdminRolePass() throws Exception {
        when(indexRebuildService.triggerFullRebuild(any())).thenReturn(
                IndexTaskProgressResponse.builder().taskNo("SR_002").createdBy("root_admin").build()
        );

        String token = userToken("root_admin", List.of(), List.of("ADMIN"));

        mockMvc.perform(post("/api/v1/search/admin/rebuild-index")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.taskNo").value("SR_002"));
    }

    @Test
    @DisplayName("安全验签：非信任密钥签发的 Token 访问受保护端点报 401 UNAUTHENTICATED")
    void testUntrustedKeyTokenReturns401() throws Exception {
        TestJwtKeys untrustedKey = new TestJwtKeys();
        Map<String, Object> claims = userClaims("untrusted_user", List.of("search:rebuild"), List.of("ADMIN"), Instant.now());
        String token = untrustedKey.signedToken(claims);

        mockMvc.perform(post("/api/v1/search/admin/rebuild-index")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("安全验签：已过期的 Token 访问受保护端点报 401 UNAUTHENTICATED")
    void testExpiredTokenReturns401() throws Exception {
        Instant issued = Instant.now().minusSeconds(400);
        Map<String, Object> claims = userClaims("expired_user", List.of("search:rebuild"), List.of("ADMIN"), issued);
        claims.put("exp", issued.plusSeconds(60)); // 过期 340 秒
        String token = TEST_KEYS.signedToken(claims);

        mockMvc.perform(post("/api/v1/search/admin/rebuild-index")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private static String userToken(String username, List<String> permissions, List<String> roles) {
        return TEST_KEYS.signedToken(userClaims(username, permissions, roles, Instant.now()));
    }

    private static Map<String, Object> userClaims(
            String username, List<String> permissions, List<String> roles, Instant now) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", ISSUER);
        claims.put("aud", List.of(AUDIENCE));
        claims.put("exp", now.plusSeconds(300));
        claims.put("nbf", now.minusSeconds(1));
        claims.put("iat", now.minusSeconds(1));
        claims.put("sub", "1001");
        claims.put("username", username);
        claims.put("roles", roles);
        claims.put("permissions", permissions);
        return claims;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestInfrastructure {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        RequestIdPolicy requestIdPolicy() {
            return new RequestIdPolicy(UUID::randomUUID);
        }

        @Bean
        RequestContextAccessor requestContextAccessor(RequestIdPolicy requestIdPolicy) {
            return new ServletRequestContextAccessor(requestIdPolicy, null);
        }

        @Bean
        ApiResponseFactory apiResponseFactory(
                RequestContextAccessor requestContextAccessor, Clock clock) {
            return new ApiResponseFactory(requestContextAccessor, clock);
        }
    }
}
