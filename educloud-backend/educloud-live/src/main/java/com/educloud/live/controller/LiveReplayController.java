package com.educloud.live.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.live.dto.response.LiveReplayResponse;
import com.educloud.live.security.JwtSecurityUtils;
import com.educloud.live.service.LiveReplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/live-rooms/{roomId}/replays")
@RequiredArgsConstructor
public class LiveReplayController {

    private final LiveReplayService liveReplayService;
    private final ApiResponseFactory responses;

    @GetMapping
    @PreAuthorize("hasAuthority('live:view')")
    public ApiResponse<List<LiveReplayResponse>> listReplays(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("roomId") Long roomId) {
        Long userId = JwtSecurityUtils.userId(jwt);
        boolean isTeacherOrAdmin = JwtSecurityUtils.isTeacher(jwt) || JwtSecurityUtils.isAdmin(jwt);
        return responses.success(liveReplayService.listReplaysByRoom(roomId, userId, isTeacherOrAdmin));
    }

    @GetMapping("/{replayId}")
    @PreAuthorize("hasAuthority('live:view')")
    public ApiResponse<LiveReplayResponse> getReplay(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("roomId") Long roomId,
            @PathVariable("replayId") Long replayId) {
        Long userId = JwtSecurityUtils.userId(jwt);
        boolean isTeacherOrAdmin = JwtSecurityUtils.isTeacher(jwt) || JwtSecurityUtils.isAdmin(jwt);
        return responses.success(liveReplayService.getReplayDetail(replayId, userId, isTeacherOrAdmin));
    }

    @DeleteMapping("/{replayId}")
    @PreAuthorize("hasAuthority('live:manage')")
    public ApiResponse<Void> deleteReplay(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("roomId") Long roomId,
            @PathVariable("replayId") Long replayId) {
        Long userId = JwtSecurityUtils.userId(jwt);
        boolean isAdmin = JwtSecurityUtils.isAdmin(jwt);
        liveReplayService.deleteReplay(replayId, userId, isAdmin);
        return responses.success(null);
    }
}
