package com.educloud.notification.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.notification.dto.response.EmailChannelStatusResponse;
import com.educloud.notification.service.EmailChannelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailChannelStatusControllerTest {

    @Mock
    private EmailChannelService emailChannelService;

    private EmailChannelStatusController controller;

    @BeforeEach
    void setUp() {
        var requestIdPolicy = new RequestIdPolicy(UUID::randomUUID);
        var requestContext = new ServletRequestContextAccessor(requestIdPolicy, null);
        var responses = new ApiResponseFactory(
                requestContext,
                Clock.fixed(Instant.parse("2026-08-25T08:00:00Z"), ZoneOffset.UTC));
        controller = new EmailChannelStatusController(emailChannelService, responses);
    }

    @Test
    @DisplayName("获取邮件渠道脱敏状态测试")
    void testGetStatus() {
        EmailChannelStatusResponse status = EmailChannelStatusResponse.builder()
                .provider("smtp")
                .host("smtp.educloud.local")
                .port(465)
                .username("sup***@educloud.cn")
                .from("EduCloud <support@educloud.cn>")
                .sslEnabled(true)
                .passwordConfigured(true)
                .build();

        when(emailChannelService.getEmailChannelStatus()).thenReturn(status);

        var response = controller.getStatus();
        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.data().getUsername()).isEqualTo("sup***@educloud.cn");
        assertThat(response.data().isPasswordConfigured()).isTrue();
    }

    @Test
    @DisplayName("管理员自测发信测试")
    void testTestSend() {
        Jwt jwt = new Jwt(
                "mock-token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", "9000000000000000002", "email", "admin@educloud.cn", "roles", List.of("ADMIN"))
        );

        var response = controller.testSend(jwt, null);
        assertThat(response.code()).isEqualTo("SUCCESS");
        verify(emailChannelService).testSendEmail(eq(9000000000000000002L), eq("admin@educloud.cn"), any());
    }
}
