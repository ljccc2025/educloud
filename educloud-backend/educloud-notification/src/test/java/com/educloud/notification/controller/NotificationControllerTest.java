package com.educloud.notification.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.notification.dto.response.NotificationResponse;
import com.educloud.notification.dto.response.UnreadCountResponse;
import com.educloud.notification.enums.NotificationKind;
import com.educloud.notification.enums.TargetType;
import com.educloud.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private NotificationController controller;

    private Jwt mockJwt;

    @BeforeEach
    void setUp() {
        var requestIdPolicy = new RequestIdPolicy(UUID::randomUUID);
        var requestContext = new ServletRequestContextAccessor(requestIdPolicy, null);
        var responses = new ApiResponseFactory(
                requestContext,
                Clock.fixed(Instant.parse("2026-08-25T08:00:00Z"), ZoneOffset.UTC));
        controller = new NotificationController(notificationService, responses);

        mockJwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", "3001", "roles", List.of("STUDENT"))
        );
    }

    @Test
    @DisplayName("用户查询收件箱列表与未读数测试")
    void testGetNotificationsAndUnreadCount() {
        NotificationResponse item = NotificationResponse.builder()
                .id(101L)
                .notificationId(501L)
                .title("微服务开课通知")
                .content("欢迎进入课程")
                .kind(NotificationKind.COURSE)
                .targetType(TargetType.USER)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();

        PageResponse<NotificationResponse> pageData = PageResponse.of(List.of(item), 1, 20, 1L);
        when(notificationService.getMyNotifications(3001L, 1, 20, null, null)).thenReturn(pageData);

        var response = controller.getMyNotifications(mockJwt, 1, 20, null, null);
        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.data().items()).hasSize(1);
        assertThat(response.data().items().get(0).getTitle()).isEqualTo("微服务开课通知");

        when(notificationService.getUnreadCount(3001L)).thenReturn(new UnreadCountResponse(1L));
        var unreadResp = controller.getUnreadCount(mockJwt);
        assertThat(unreadResp.code()).isEqualTo("SUCCESS");
        assertThat(unreadResp.data().getUnreadCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("用户单条已读、全量已读与删除测试")
    void testReadAndOperations() {
        var resp1 = controller.markAsRead(mockJwt, 101L);
        assertThat(resp1.code()).isEqualTo("SUCCESS");
        verify(notificationService).markAsRead(3001L, 101L);

        var resp2 = controller.markAllAsRead(mockJwt);
        assertThat(resp2.code()).isEqualTo("SUCCESS");
        verify(notificationService).markAllAsRead(3001L);

        var resp3 = controller.deleteNotification(mockJwt, 101L);
        assertThat(resp3.code()).isEqualTo("SUCCESS");
        verify(notificationService).deleteNotification(3001L, 101L);
    }
}
