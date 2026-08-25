package com.educloud.live.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.web.RequestContextFilter;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.live.dto.request.LiveRoomCreateRequest;
import com.educloud.live.dto.response.LiveRoomDetailResponse;
import com.educloud.live.dto.response.LiveStartResponse;
import com.educloud.live.dto.response.LiveTicketResponse;
import com.educloud.live.enums.LiveProviderType;
import com.educloud.live.enums.LiveRoomStatus;
import com.educloud.live.exception.LiveExceptionHandler;
import com.educloud.live.service.LiveLifecycleService;
import com.educloud.live.service.LiveTicketService;
import com.educloud.live.spi.model.LiveStreamPushUrl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LiveRoomControllerTest {

    private MockMvc mockMvc;
    private LiveLifecycleService lifecycleService;
    private LiveTicketService ticketService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        lifecycleService = mock(LiveLifecycleService.class);
        ticketService = mock(LiveTicketService.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        var requestIdPolicy = new RequestIdPolicy(UUID::randomUUID);
        var requestContext = new ServletRequestContextAccessor(requestIdPolicy, null);
        var responses = new ApiResponseFactory(
                requestContext,
                Clock.fixed(Instant.parse("2026-08-25T08:00:00Z"), ZoneOffset.UTC));

        LiveRoomController controller = new LiveRoomController(lifecycleService, ticketService, responses);
        LiveExceptionHandler exceptionHandler = new LiveExceptionHandler(responses);

        Jwt mockJwt = new Jwt(
                "token",
                Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of("sub", "9001", "roles", List.of("TEACHER"), "permissions", List.of("live:create", "live:manage", "live:view", "live:join")));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                    }

                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest,
                            WebDataBinderFactory binderFactory) {
                        return mockJwt;
                    }
                })
                .addFilters(new RequestContextFilter(requestIdPolicy))
                .build();
    }

    @Test
    void testCreateRoomSuccess() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        LiveRoomDetailResponse detail = LiveRoomDetailResponse.builder()
                .id(1001L)
                .courseId(2001L)
                .teacherId(9001L)
                .title("微服务架构设计与实战")
                .status(LiveRoomStatus.CREATED)
                .providerType(LiveProviderType.MOCK)
                .build();
        when(lifecycleService.createRoom(eq(9001L), anyBoolean(), any())).thenReturn(detail);

        LiveRoomCreateRequest request = LiveRoomCreateRequest.builder()
                .courseId(2001L)
                .title("微服务架构设计与实战")
                .scheduledStartAt(now.plusHours(1))
                .scheduledEndAt(now.plusHours(2))
                .build();

        mockMvc.perform(post("/api/v1/live-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value("1001"))
                .andExpect(jsonPath("$.data.title").value("微服务架构设计与实战"));
    }

    @Test
    void testStartLiveSuccess() throws Exception {
        LiveStartResponse startResponse = LiveStartResponse.builder()
                .roomId(1001L)
                .sessionId(8001L)
                .streamKey("stream_1001")
                .pushInfo(LiveStreamPushUrl.builder()
                        .pushUrl("rtmp://live-mock.educloud.cn/live/stream_1001?sign=abc")
                        .streamKey("stream_1001")
                        .build())
                .startedAt(LocalDateTime.now())
                .build();
        when(lifecycleService.startLive(eq(1001L), eq(9001L), anyBoolean())).thenReturn(startResponse);

        mockMvc.perform(post("/api/v1/live-rooms/1001/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.roomId").value("1001"))
                .andExpect(jsonPath("$.data.sessionId").value("8001"));
    }

    @Test
    void testIssueTicketSuccess() throws Exception {
        LiveTicketResponse ticketResponse = LiveTicketResponse.builder()
                .ticket("ticket_test_abc123")
                .roomId(1001L)
                .wsEndpoint("/ws/v1/live/1001?ticket=ticket_test_abc123")
                .expiresInSeconds(60L)
                .build();
        when(ticketService.issueConnectionTicket(eq(1001L), eq(9001L), any(), anyBoolean())).thenReturn(ticketResponse);

        mockMvc.perform(post("/api/v1/live-rooms/1001/connection-ticket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.ticket").value("ticket_test_abc123"));
    }

    @Test
    void testGetRoomDetailSuccess() throws Exception {
        LiveRoomDetailResponse detail = LiveRoomDetailResponse.builder()
                .id(1001L)
                .courseId(2001L)
                .teacherId(9001L)
                .title("微服务架构设计与实战")
                .status(LiveRoomStatus.LIVING)
                .currentOnlineViewers(42)
                .build();
        when(lifecycleService.getRoomDetail(eq(1001L), eq(9001L), anyBoolean())).thenReturn(detail);

        mockMvc.perform(get("/api/v1/live-rooms/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value("1001"))
                .andExpect(jsonPath("$.data.currentOnlineViewers").value(42));
    }

    @Test
    void testListRoomsSuccess() throws Exception {
        Page<LiveRoomDetailResponse> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(LiveRoomDetailResponse.builder()
                .id(1001L)
                .title("微服务")
                .build()));
        when(lifecycleService.listRooms(any(), eq(9001L))).thenReturn(page);

        mockMvc.perform(get("/api/v1/live-rooms?page=1&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].id").value("1001"));
    }
}
