package com.educloud.content.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.content.config.ContentProperties;
import com.educloud.content.config.SecurityConfig;
import com.educloud.content.dto.response.ChapterResponse;
import com.educloud.content.dto.response.ContentDraftResponse;
import com.educloud.content.dto.response.CourseProgressResponse;
import com.educloud.content.dto.response.CoursewareDownloadUrlResponse;
import com.educloud.content.security.TeacherAccessGuard;
import com.educloud.content.service.ChapterService;
import com.educloud.content.service.ContentAuditService;
import com.educloud.content.service.ContentRevisionService;
import com.educloud.content.service.CourseContentService;
import com.educloud.content.service.CourseProgressService;
import com.educloud.content.service.CoursewareAccessService;
import com.educloud.content.service.CoursewareService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        ContentPublicController.class,
        ContentTeacherController.class,
        ContentStudentController.class,
        ContentAdminController.class
})
@Import({SecurityConfig.class, ContentControllerTest.TestConfig.class})
class ContentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseContentService courseContentService;
    @MockBean
    private ChapterService chapterService;
    @MockBean
    private CoursewareService coursewareService;
    @MockBean
    private ContentRevisionService revisionService;
    @MockBean
    private TeacherAccessGuard teacherAccessGuard;
    @MockBean
    private CoursewareAccessService coursewareAccessService;
    @MockBean
    private CourseProgressService progressService;
    @MockBean
    private ContentAuditService auditService;
    @MockBean
    private JwtDecoder jwtDecoder;

    @TestConfiguration
    static class TestConfig {
        @Bean
        ApiResponseFactory apiResponseFactory(RequestContextAccessor accessor) {
            return new ApiResponseFactory(accessor, java.time.Clock.systemUTC());
        }

        @Bean
        RequestContextAccessor requestContextAccessor() {
            return new ServletRequestContextAccessor(new RequestIdPolicy(java.util.UUID::randomUUID), null);
        }
    }

    @Test
    void publicChapters_accessibleWithoutToken() throws Exception {
        ChapterResponse ch = new ChapterResponse();
        ch.setId(401L);
        ch.setTitle("Chapter 1");
        ch.setCoursewares(List.of());

        when(courseContentService.getPublishedChapters(eq(110L), any())).thenReturn(List.of(ch));

        mockMvc.perform(get("/api/v1/courses/110/chapters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].id").value("401"))
                .andExpect(jsonPath("$.data[0].title").value("Chapter 1"));
    }

    @Test
    void teacherDraft_requiresValidToken() throws Exception {
        Jwt jwt = createJwt("9000000000000000001", Set.of("TEACHER"), Set.of("content:manage"));
        when(jwtDecoder.decode(eq("teacher-token"))).thenReturn(jwt);
        // BUG-005 修复后端点改用归属校验重载（Jwt, courseId），stub 需匹配新签名。
        when(teacherAccessGuard.checkTeacherAccess(any(), any())).thenReturn(9000000000000000001L);

        ContentDraftResponse draft = new ContentDraftResponse();
        draft.setCourseId(110L);
        draft.setRevisionId(320L);
        draft.setRevisionNo(1);
        draft.setRevisionStatus("DRAFT");
        draft.setChapters(List.of());

        when(courseContentService.getOrCreateDraft(eq(110L), eq(9000000000000000001L))).thenReturn(draft);

        mockMvc.perform(get("/api/v1/teacher/courses/110/content-draft")
                        .header("Authorization", "Bearer teacher-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.courseId").value("110"))
                .andExpect(jsonPath("$.data.revisionStatus").value("DRAFT"));
    }

    @Test
    void studentDownloadUrl_accessible() throws Exception {
        CoursewareDownloadUrlResponse resp = new CoursewareDownloadUrlResponse();
        resp.setCoursewareId(501L);
        resp.setDownloadUrl("http://video.stream/sample.mp4");
        resp.setExpiresAt(LocalDateTime.now().plusMinutes(15));

        when(coursewareAccessService.getDownloadUrl(eq(501L), any(), any(), any())).thenReturn(resp);

        mockMvc.perform(get("/api/v1/coursewares/501/download-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.downloadUrl").value("http://video.stream/sample.mp4"));
    }

    private static Jwt createJwt(String subject, Set<String> roles, Set<String> permissions) {
        return Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("sub", subject)
                .claim("sid", "mock-session")
                .claim("userType", "TEACHER")
                .claim("tokenVersion", 1)
                .claim("roles", List.copyOf(roles))
                .claim("permissions", List.copyOf(permissions))
                .claim("aud", List.of("educloud-api"))
                .claim("iss", "https://issuer.educloud.local")
                .claim("exp", Instant.now().plusSeconds(3600))
                .claim("nbf", Instant.now().minusSeconds(10))
                .claim("iat", Instant.now().minusSeconds(10))
                .build();
    }
}
