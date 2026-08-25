package com.educloud.live.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.live.dto.request.LiveRoomCreateRequest;
import com.educloud.live.dto.request.LiveRoomPageQuery;
import com.educloud.live.dto.request.LiveRoomUpdateRequest;
import com.educloud.live.dto.response.LiveEndResponse;
import com.educloud.live.dto.response.LiveRoomDetailResponse;
import com.educloud.live.dto.response.LiveStartResponse;
import com.educloud.live.dto.response.LiveTicketResponse;
import com.educloud.live.enums.LiveRoomStatus;
import com.educloud.live.security.JwtSecurityUtils;
import com.educloud.live.service.LiveLifecycleService;
import com.educloud.live.service.LiveTicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/live-rooms")
@RequiredArgsConstructor
public class LiveRoomController {

    private final LiveLifecycleService lifecycleService;
    private final LiveTicketService ticketService;
    private final ApiResponseFactory responses;

    @PostMapping
    @PreAuthorize("hasAuthority('live:create')")
    public ApiResponse<LiveRoomDetailResponse> createRoom(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody LiveRoomCreateRequest request) {
        Long userId = JwtSecurityUtils.userId(jwt);
        boolean isAdmin = JwtSecurityUtils.isAdmin(jwt);
        return responses.success(lifecycleService.createRoom(userId, isAdmin, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('live:manage')")
    public ApiResponse<LiveRoomDetailResponse> updateRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") Long id,
            @Valid @RequestBody LiveRoomUpdateRequest request) {
        Long userId = JwtSecurityUtils.userId(jwt);
        boolean isAdmin = JwtSecurityUtils.isAdmin(jwt);
        return responses.success(lifecycleService.updateRoom(id, userId, isAdmin, request));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('live:manage')")
    public ApiResponse<LiveStartResponse> startLive(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") Long id) {
        Long userId = JwtSecurityUtils.userId(jwt);
        boolean isAdmin = JwtSecurityUtils.isAdmin(jwt);
        return responses.success(lifecycleService.startLive(id, userId, isAdmin));
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasAuthority('live:manage')")
    public ApiResponse<LiveEndResponse> endLive(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") Long id) {
        Long userId = JwtSecurityUtils.userId(jwt);
        boolean isAdmin = JwtSecurityUtils.isAdmin(jwt);
        return responses.success(lifecycleService.endLive(id, userId, isAdmin));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('live:manage')")
    public ApiResponse<Void> cancelRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") Long id) {
        Long userId = JwtSecurityUtils.userId(jwt);
        boolean isAdmin = JwtSecurityUtils.isAdmin(jwt);
        lifecycleService.cancelRoom(id, userId, isAdmin);
        return responses.success(null);
    }

    @PostMapping("/{id}/connection-ticket")
    @PreAuthorize("hasAuthority('live:join')")
    public ApiResponse<LiveTicketResponse> issueConnectionTicket(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") Long id) {
        Long userId = JwtSecurityUtils.userId(jwt);
        String userName = JwtSecurityUtils.userName(jwt);
        boolean isTeacherOrAdmin = JwtSecurityUtils.isTeacher(jwt) || JwtSecurityUtils.isAdmin(jwt);
        return responses.success(ticketService.issueConnectionTicket(id, userId, userName, isTeacherOrAdmin));
    }

    @PutMapping("/{id}/mute")
    @PreAuthorize("hasAuthority('live:moderate')")
    public ApiResponse<Void> toggleMute(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") Long id,
            @RequestBody Map<String, Boolean> body) {
        Long userId = JwtSecurityUtils.userId(jwt);
        boolean isTeacherOrAdmin = JwtSecurityUtils.isTeacher(jwt) || JwtSecurityUtils.isAdmin(jwt);
        Boolean allowChat = body != null ? body.get("allowChat") : null;
        lifecycleService.toggleMute(id, userId, isTeacherOrAdmin, allowChat);
        return responses.success(null);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('live:view')")
    public ApiResponse<LiveRoomDetailResponse> getRoomDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") Long id) {
        Long userId = JwtSecurityUtils.userId(jwt);
        boolean isTeacherOrAdmin = JwtSecurityUtils.isTeacher(jwt) || JwtSecurityUtils.isAdmin(jwt);
        return responses.success(lifecycleService.getRoomDetail(id, userId, isTeacherOrAdmin));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('live:view')")
    public ApiResponse<PageResponse<LiveRoomDetailResponse>> listRooms(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) LiveRoomStatus status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Long userId = JwtSecurityUtils.userId(jwt);
        LiveRoomPageQuery query = LiveRoomPageQuery.builder()
                .courseId(courseId)
                .teacherId(teacherId)
                .status(status)
                .page(page)
                .size(size)
                .build();
        Page<LiveRoomDetailResponse> result = lifecycleService.listRooms(query, userId);
        return responses.success(PageResponse.of(result.getRecords(), (int) result.getCurrent(), (int) result.getSize(), result.getTotal()));
    }
}
