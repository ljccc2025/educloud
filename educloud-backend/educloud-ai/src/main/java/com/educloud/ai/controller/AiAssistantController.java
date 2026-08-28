package com.educloud.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.ai.dto.request.AiChatRequest;
import com.educloud.ai.dto.response.AiChatResponse;
import com.educloud.ai.dto.response.AiConversationResponse;
import com.educloud.ai.dto.response.AiMessageResponse;
import com.educloud.ai.chat.ChatService;
import com.educloud.ai.entity.AiConversationEntity;
import com.educloud.ai.entity.AiMessageEntity;
import com.educloud.ai.exception.AiErrorCode;
import com.educloud.ai.mapper.AiConversationMapper;
import com.educloud.ai.mapper.AiMessageMapper;
import com.educloud.ai.security.JwtSecurityUtils;
import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.common.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 助教接口（规格 §4，路径 /api/v1/ai/**，网关 ai-core 路由）。
 * 身份只取 JWT sub；STUDENT 守卫读 roles claim（permissions 码不含 ROLE_ 前缀，hasRole 不可用）。
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiAssistantController {

    private final ChatService chatService;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final ApiResponseFactory responses;

    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(@RequestBody AiChatRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(chatService.chat(requireStudentId(jwt), request));
    }

    @GetMapping("/conversations")
    public ApiResponse<PageResponse<AiConversationResponse>> listConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        Long studentId = requireStudentId(jwt);
        Page<AiConversationEntity> result = conversationMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 50)),
                new LambdaQueryWrapper<AiConversationEntity>()
                        .eq(AiConversationEntity::getStudentId, studentId)
                        .eq(AiConversationEntity::getDeleted, 0)
                        .orderByDesc(AiConversationEntity::getLastMessageAt));
        List<AiConversationResponse> items = result.getRecords().stream().map(AiAssistantController::toResponse).toList();
        return responses.success(PageResponse.of(items, (int) result.getCurrent(),
                (int) result.getSize(), result.getTotal()));
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<AiMessageResponse>> listMessages(@PathVariable Long id,
                                                             @AuthenticationPrincipal Jwt jwt) {
        Long studentId = requireStudentId(jwt);
        requireOwnedConversation(id, studentId);
        return responses.success(messageMapper.selectList(
                        new LambdaQueryWrapper<AiMessageEntity>()
                                .eq(AiMessageEntity::getConversationId, id)
                                .orderByAsc(AiMessageEntity::getId))
                .stream()
                .filter(row -> !"FAILED".equals(row.getStatus()))
                .map(AiAssistantController::toMessageResponse)
                .toList());
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable Long id,
                                                   @AuthenticationPrincipal Jwt jwt) {
        Long studentId = requireStudentId(jwt);
        AiConversationEntity entity = requireOwnedConversation(id, studentId);
        entity.setDeleted(1);
        entity.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(entity);
        return ResponseEntity.noContent().build();
    }

    private Long requireStudentId(Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        if (!JwtSecurityUtils.roles(jwt).contains("STUDENT")) {
            throw new BusinessException(AiErrorCode.AI_ACCESS_DENIED,
                    "AI assistant is available to students only: subject=" + jwt.getSubject());
        }
        return studentId;
    }

    private AiConversationEntity requireOwnedConversation(Long id, Long studentId) {
        AiConversationEntity entity = conversationMapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BusinessException(AiErrorCode.AI_CONVERSATION_NOT_FOUND,
                    "AI conversation not found: " + id);
        }
        if (!entity.getStudentId().equals(studentId)) {
            throw new BusinessException(AiErrorCode.AI_CONVERSATION_NOT_OWNED,
                    "AI conversation " + id + " does not belong to student " + studentId);
        }
        return entity;
    }

    private static AiConversationResponse toResponse(AiConversationEntity entity) {
        return AiConversationResponse.builder()
                .id(String.valueOf(entity.getId()))
                .title(entity.getTitle())
                .messageCount(entity.getMessageCount())
                .lastMessageAt(entity.getLastMessageAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private static AiMessageResponse toMessageResponse(AiMessageEntity entity) {
        return AiMessageResponse.builder()
                .id(String.valueOf(entity.getId()))
                .role(entity.getRole())
                .content(entity.getContent())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
