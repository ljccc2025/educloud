package com.educloud.ai.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.educloud.ai.config.AiProperties;
import com.educloud.ai.dto.request.AiChatRequest;
import com.educloud.ai.dto.response.AiChatResponse;
import com.educloud.ai.dto.response.AiUsageResponse;
import com.educloud.ai.entity.AiConversationEntity;
import com.educloud.ai.entity.AiMessageEntity;
import com.educloud.ai.exception.AiErrorCode;
import com.educloud.ai.mapper.AiConversationMapper;
import com.educloud.ai.mapper.AiMessageMapper;
import com.educloud.ai.provider.AiProviderException;
import com.educloud.ai.provider.ChatProvider;
import com.educloud.ai.provider.ChatProvider.ChatOptions;
import com.educloud.ai.provider.ChatProvider.ChatResult;
import com.educloud.common.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 提问编排（规格 §4/§5）：校验 → 配额 → 会话归属 → user 行落库 → 调模型 → assistant 行落库 → 计数。
 * user 行在外部调用前提交（审计先行）；外部调用不包在数据库事务里（25s 调用不占连接池事务）。
 * 失败仍写 assistant 行（status=FAILED + error_code），保证审计完整；失败调用不计次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 规格 §5.5：system prompt 固定服务端，强调纯文本 + **加粗**（前端行内渲染），禁止编造平台数据。 */
    static final String SYSTEM_PROMPT = "你是 EduCloud 在线教育平台的 AI 助教。请遵守："
            + "1) 用中文分步讲解，用 1. 2. 3. 这样的纯文本编号；"
            + "2) 不要使用标题、列表符号、代码块等任何 markdown 结构标记；需要强调关键词时可以用 **关键词** 的形式加粗；"
            + "3) 不得编造平台内的课程、作业、成绩等数据，相关提问请引导学生在平台内查看；"
            + "4) 回答保持精炼，先给结论再给步骤。";

    private static final int MAX_QUESTION_LENGTH = 1000;
    private static final int TITLE_MAX_LENGTH = 120;
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String STATUS_OK = "OK";
    private static final String STATUS_TRUNCATED = "TRUNCATED";
    private static final String STATUS_FAILED = "FAILED";

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final ChatProvider chatProvider;
    private final ContextAssembler contextAssembler;
    private final QuotaService quotaService;
    private final TransactionTemplate transactionTemplate;
    private final AiProperties properties;

    public AiChatResponse chat(Long studentId, AiChatRequest request) {
        if (Boolean.TRUE.equals(request.getStream())) {
            throw new BusinessException(AiErrorCode.AI_STREAM_NOT_SUPPORTED,
                    "P1 does not support streaming responses; omit stream or set stream=false");
        }
        String question = request.getQuestion() == null ? "" : request.getQuestion().trim();
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new BusinessException(AiErrorCode.AI_QUESTION_TOO_LONG,
                    "Question length " + question.length() + " exceeds " + MAX_QUESTION_LENGTH);
        }

        quotaService.ensureWithinLimits(studentId);

        AiConversationEntity conversation = resolveConversation(studentId, request.getConversationId(), question);
        List<ChatTurn> history = loadRecentHistory(conversation.getId());

        transactionTemplate.executeWithoutResult(status ->
                messageMapper.insert(userRow(conversation.getId(), question)));

        ChatResult result;
        try {
            List<ChatTurn> messages = contextAssembler.assemble(SYSTEM_PROMPT, history, question);
            result = chatProvider.chat(messages, new ChatOptions(properties.provider().maxTokens()));
        } catch (AiProviderException exception) {
            log.error("AI provider call failed: upstreamStatus={}, retryable={}",
                    exception.upstreamStatus(), exception.retryable());
            AiMessageEntity failedRow = failedAssistantRow(conversation.getId(),
                    AiErrorCode.AI_PROVIDER_UNAVAILABLE.name());
            transactionTemplate.executeWithoutResult(status -> messageMapper.insert(failedRow));
            bumpConversationCounters(conversation.getId());
            throw new BusinessException(AiErrorCode.AI_PROVIDER_UNAVAILABLE,
                    "AI provider is unavailable, please retry later");
        }

        AiMessageEntity assistantRow = assistantRow(conversation.getId(), result);
        transactionTemplate.executeWithoutResult(status -> messageMapper.insert(assistantRow));
        bumpConversationCounters(conversation.getId());
        quotaService.recordUsage(studentId, result.totalTokens());
        // 日志纪律（规格 §5.6）：只记长度/条数/usage/latency/finish_reason，绝不记原文与密钥
        log.info("AI chat answered: questionChars={}, historyTurns={}, promptTokens={}, completionTokens={}, "
                        + "finishReason={}, latencyMs={}",
                question.length(), history.size(), result.promptTokens(), result.completionTokens(),
                result.finishReason(), result.latencyMs());

        return AiChatResponse.builder()
                .conversationId(String.valueOf(conversation.getId()))
                .messageId(String.valueOf(assistantRow.getId()))
                .content(result.content())
                .finishReason(result.finishReason())
                .usage(new AiUsageResponse(result.promptTokens(), result.completionTokens(), result.totalTokens()))
                .degraded(false)
                .build();
    }

    private AiConversationEntity resolveConversation(Long studentId, Long conversationId, String question) {
        if (conversationId == null) {
            AiConversationEntity entity = new AiConversationEntity();
            entity.setStudentId(studentId);
            entity.setTitle(question.length() > TITLE_MAX_LENGTH ? question.substring(0, TITLE_MAX_LENGTH) : question);
            entity.setMessageCount(0);
            entity.setDeleted(0);
            entity.setLastMessageAt(LocalDateTime.now());
            conversationMapper.insert(entity);
            return entity;
        }
        AiConversationEntity entity = conversationMapper.selectById(conversationId);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BusinessException(AiErrorCode.AI_CONVERSATION_NOT_FOUND,
                    "AI conversation not found: " + conversationId);
        }
        if (!entity.getStudentId().equals(studentId)) {
            throw new BusinessException(AiErrorCode.AI_CONVERSATION_NOT_OWNED,
                    "AI conversation " + conversationId + " does not belong to student " + studentId);
        }
        return entity;
    }

    /**
     * 最近 N 条历史，按 id 升序返回（雪花 id 单调，规避同毫秒 created_at 排序不稳定）。
     * 最小修复（任务文本实现与测试不一致）：任务文本用 selectPage+Page(1,N,false)，但测试契约
     * 桩的是 selectList(any())；这里改用 selectList + orderByDesc + LIMIT，语义等价
     * （分页插件已配置，但单测不需要 Page）。
     */
    private List<ChatTurn> loadRecentHistory(Long conversationId) {
        List<AiMessageEntity> rows = messageMapper.selectList(
                new LambdaQueryWrapper<AiMessageEntity>()
                        .eq(AiMessageEntity::getConversationId, conversationId)
                        .orderByDesc(AiMessageEntity::getId)
                        .last("LIMIT " + properties.context().maxHistoryMessages()));
        // 最小修复：单测桩返回不可变的 List.of(...)，先拷贝再原地 reverse
        List<AiMessageEntity> asc = new ArrayList<>(rows);
        Collections.reverse(asc);
        return asc.stream()
                .map(row -> new ChatTurn(row.getRole(), row.getContent() == null ? "" : row.getContent()))
                .toList();
    }

    private AiMessageEntity userRow(Long conversationId, String question) {
        AiMessageEntity row = baseRow(conversationId, ROLE_USER, question);
        row.setStatus(STATUS_OK);
        return row;
    }

    private AiMessageEntity assistantRow(Long conversationId, ChatResult result) {
        AiMessageEntity row = baseRow(conversationId, ROLE_ASSISTANT, result.content());
        row.setStatus("length".equals(result.finishReason()) ? STATUS_TRUNCATED : STATUS_OK);
        row.setProvider(properties.provider().name());
        row.setModel(result.model());
        row.setPromptTokens(result.promptTokens());
        row.setCompletionTokens(result.completionTokens());
        row.setLatencyMs((int) Math.min(result.latencyMs(), Integer.MAX_VALUE));
        row.setFinishReason(result.finishReason());
        return row;
    }

    private AiMessageEntity failedAssistantRow(Long conversationId, String errorCode) {
        AiMessageEntity row = baseRow(conversationId, ROLE_ASSISTANT, "");
        row.setStatus(STATUS_FAILED);
        row.setProvider(properties.provider().name());
        row.setModel(properties.provider().model());
        row.setFinishReason("error");
        row.setErrorCode(errorCode);
        return row;
    }

    private AiMessageEntity baseRow(Long conversationId, String role, String content) {
        AiMessageEntity row = new AiMessageEntity();
        row.setConversationId(conversationId);
        row.setRole(role);
        row.setContent(content);
        return row;
    }

    private void bumpConversationCounters(Long conversationId) {
        conversationMapper.update(null, new LambdaUpdateWrapper<AiConversationEntity>()
                .eq(AiConversationEntity::getId, conversationId)
                .setSql("message_count = message_count + 2")
                .set(AiConversationEntity::getLastMessageAt, LocalDateTime.now()));
    }
}
