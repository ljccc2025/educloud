package com.educloud.live.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.live.entity.LiveMessageEntity;
import com.educloud.live.security.JwtSecurityUtils;
import com.educloud.live.service.LiveMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/live-rooms/{roomId}/messages")
@RequiredArgsConstructor
public class LiveMessageController {

    private final LiveMessageService liveMessageService;
    private final ApiResponseFactory responses;

    @PostMapping("/{messageId}/recall")
    @PreAuthorize("hasAuthority('live:moderate') or hasAuthority('live:join')")
    public ApiResponse<Void> recallMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("roomId") Long roomId,
            @PathVariable("messageId") Long messageId) {
        Long userId = JwtSecurityUtils.userId(jwt);
        boolean isTeacherOrAdmin = JwtSecurityUtils.isTeacher(jwt) || JwtSecurityUtils.isAdmin(jwt);
        liveMessageService.recallMessage(messageId, userId, isTeacherOrAdmin);
        return responses.success(null);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('live:view')")
    public ApiResponse<List<LiveMessageEntity>> listMessages(
            @PathVariable("roomId") Long roomId,
            @RequestParam(defaultValue = "50") Integer limit) {
        return responses.success(liveMessageService.listMessages(roomId, limit));
    }
}
