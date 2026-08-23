package com.educloud.user.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.user.dto.response.ProfileResponse;
import com.educloud.user.dto.response.UserSummary;
import com.educloud.user.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /me 与档案 Web 切片测试。依据：M03 计划任务 10（身份来自 JWT sub、本人校验、
 * 校验失败 400、未认证 401；头像 fileId 不触发 File 调用）。
 */
@WebMvcTest(MeController.class)
class MeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProfileService profileService;

    @MockBean
    private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @MockBean
    private com.educloud.user.config.InternalProperties internalProperties;

    @MockBean
    private com.educloud.common.api.ApiResponseFactory responses;

    @BeforeEach
    void stubResponses() {
        java.time.Instant now = java.time.Instant.parse("2026-08-21T10:00:00Z");
        org.mockito.Mockito.when(responses.success(org.mockito.ArgumentMatchers.any(UserSummary.class)))
                .thenAnswer(invocation -> new ApiResponse<>(
                        "SUCCESS", "OK", invocation.getArgument(0), "req-1", now));
        org.mockito.Mockito.when(responses.success(org.mockito.ArgumentMatchers.any(ProfileResponse.class)))
                .thenAnswer(invocation -> new ApiResponse<>(
                        "SUCCESS", "OK", invocation.getArgument(0), "req-1", now));
    }

    private static final SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor STUDENT_JWT =
            jwt().jwt(builder -> builder.subject("1001"));

    @Test
    void meReturnsCurrentUserSummary() throws Exception {
        when(profileService.me(1001L)).thenReturn(new UserSummary(
                "1001", "student01", "学生01", "STUDENT",
                List.of("STUDENT"), List.of("course:read"), null, "99"));

        mockMvc.perform(get("/api/v1/me").with(STUDENT_JWT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.username").value("student01"))
                .andExpect(jsonPath("$.data.userType").value("STUDENT"));
    }

    @Test
    void meRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfileValidatesRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/me/profile")
                        .with(STUDENT_JWT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}