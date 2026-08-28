package com.educloud.ai.chat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.educloud.ai.config.AiProperties;
import com.educloud.ai.dto.request.AiChatRequest;
import com.educloud.ai.dto.response.AiChatResponse;
import com.educloud.ai.entity.AiConversationEntity;
import com.educloud.ai.entity.AiMessageEntity;
import com.educloud.ai.exception.AiErrorCode;
import com.educloud.ai.mapper.AiConversationMapper;
import com.educloud.ai.mapper.AiMessageMapper;
import com.educloud.ai.provider.AiProviderException;
import com.educloud.ai.provider.ChatProvider;
// 最小修复（任务文本测试代码编译问题）：ChatResult 是 ChatProvider 的嵌套 record，
// 顶层 com.educloud.ai.provider.ChatResult 不存在（OpenAiCompatibleProviderTest 同款修复）。
import com.educloud.ai.provider.ChatProvider.ChatResult;
import com.educloud.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

// 最小修复（任务文本测试代码占位符问题）：任务文本里 SYSTEM_PROMPT = "你是 EduCloud AI 助教……"
// 是占位符，与 ChatService.SYSTEM_PROMPT 真值不一致会让 eq(SYSTEM_PROMPT) 桩失配
// （strict stubs 直接抛 PotentialStubbingProblem）；改为引用 ChatService 的包级常量（同包可见）。
import static com.educloud.ai.chat.ChatService.SYSTEM_PROMPT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final Long STUDENT_ID = 2001L;
    private static final Long OTHER_STUDENT_ID = 9999L;

    @Mock
    private AiConversationMapper conversationMapper;
    @Mock
    private AiMessageMapper messageMapper;
    @Mock
    private ChatProvider chatProvider;
    @Mock
    private ContextAssembler contextAssembler;
    @Mock
    private QuotaService quotaService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        // 最小修复（MP 单测环境问题）：实现里 LambdaUpdateWrapper.set() 会急切解析列名
        // （columnToString → LambdaUtils 列缓存），该缓存只有 MyBatis-Plus 运行时初始化 mapper 时才建立；
        // 纯单测需手动初始化两个实体的 TableInfo，否则抛 "can not find lambda cache for this entity"。
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                AiConversationEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                AiMessageEntity.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        // 单测里直接同步执行事务体。
        // 最小修复：stream/超长/配额/404/403 短路用例不会走到任何事务，strict stubs 会报
        // UnnecessaryStubbingException，与消息插入桩一样需要 lenient。
        lenient().doAnswer(invocation -> {
            invocation.<java.util.function.Consumer<org.springframework.transaction.TransactionStatus>>getArgument(0)
                    .accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        // 消息插入回填雪花 id（部分用例不落库，需 lenient）。
        // 最小修复（任务文本测试代码运行时问题）：getArgument(0) 的泛型 T 会被 setField 的
        // setField(Class,String,Object) 重载推断捕获，运行时抛 ClassCastException；
        // 显式转 Object 强制走 setField(Object,String,Object) 重载。
        java.util.concurrent.atomic.AtomicLong seq = new java.util.concurrent.atomic.AtomicLong(1000);
        lenient().doAnswer(invocation -> {
            ReflectionTestUtils.setField((Object) invocation.getArgument(0), "id", seq.incrementAndGet());
            return 1;
        }).when(messageMapper).insert(any(AiMessageEntity.class));
        chatService = new ChatService(conversationMapper, messageMapper, chatProvider,
                contextAssembler, quotaService, transactionTemplate, properties());
    }

    private static AiProperties properties() {
        return new AiProperties(
                new AiProperties.ProviderProperties("openai-compatible", "https://x/v1", "Qwen/Qwen3.6-27B", "k", false, 1024),
                new AiProperties.TimeoutProperties(5000, 25000),
                new AiProperties.QuotaProperties(50, 2000000),
                new AiProperties.ContextProperties(10, 3000),
                new AiProperties.JwtProperties("", "i", "a"));
    }

    private static AiChatRequest request(Long conversationId, String question, Boolean stream) {
        AiChatRequest request = new AiChatRequest();
        request.setConversationId(conversationId);
        request.setQuestion(question);
        request.setStream(stream);
        return request;
    }

    private static AiConversationEntity conversation(Long id, Long studentId, int deleted) {
        AiConversationEntity entity = new AiConversationEntity();
        entity.setId(id);
        entity.setStudentId(studentId);
        entity.setTitle("已有会话");
        entity.setMessageCount(2);
        entity.setDeleted(deleted);
        return entity;
    }

    @Test
    void rejectsStreamTrueExplicitly() {
        assertThatThrownBy(() -> chatService.chat(STUDENT_ID, request(null, "问", true)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_STREAM_NOT_SUPPORTED);
        verify(chatProvider, never()).chat(any(), any());
    }

    @Test
    void rejectsQuestionOver1000Characters() {
        assertThatThrownBy(() -> chatService.chat(STUDENT_ID, request(null, "长".repeat(1001), false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_QUESTION_TOO_LONG);
        verify(chatProvider, never()).chat(any(), any());
    }

    @Test
    void quotaCheckHappensBeforeAnyWriteOrProviderCall() {
        doThrow(new BusinessException(AiErrorCode.AI_QUOTA_EXCEEDED, "quota"))
                .when(quotaService).ensureWithinLimits(STUDENT_ID);

        assertThatThrownBy(() -> chatService.chat(STUDENT_ID, request(null, "问", false)))
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_QUOTA_EXCEEDED);

        // 最小修复（MP 3.5.12 编译问题）：BaseMapper 有 insert(T) 与 insert(Collection<T>) 两个重载，
        // 裸 any() 二义性，需指明实体类型。
        verify(conversationMapper, never()).insert(any(AiConversationEntity.class));
        verify(messageMapper, never()).insert(any(AiMessageEntity.class));
        verify(chatProvider, never()).chat(any(), any());
    }

    @Test
    void createsConversationWithTruncatedTitleWhenIdAbsent() {
        when(contextAssembler.assemble(anyString(), any(), anyString())).thenAnswer(inv -> List.of(
                new ChatTurn("system", "s"), new ChatTurn("user", inv.getArgument(2))));
        when(chatProvider.chat(any(), any())).thenReturn(
                new ChatResult("答", "stop", 79, 291, 370, "Qwen/Qwen3.6-27B", 800L));
        doAnswer(inv -> {
            ReflectionTestUtils.setField((Object) inv.getArgument(0), "id", 111L);
            return 1;
        }).when(conversationMapper).insert(any(AiConversationEntity.class));

        AiChatResponse response = chatService.chat(STUDENT_ID, request(null, "什".repeat(200), false));

        ArgumentCaptor<AiConversationEntity> convCaptor = ArgumentCaptor.forClass(AiConversationEntity.class);
        verify(conversationMapper).insert(convCaptor.capture());
        assertThat(convCaptor.getValue().getTitle()).hasSize(120);
        assertThat(convCaptor.getValue().getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(response.getConversationId()).isEqualTo("111");
        // 最小修复（任务文本测试代码编译问题）：degraded 是 primitive boolean，Lombok @Data 生成
        // isDegraded() 而非 getDegraded()。
        assertThat(response.isDegraded()).isFalse();
    }

    @Test
    void foreignConversationRejectedWith403() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, OTHER_STUDENT_ID, 0));

        assertThatThrownBy(() -> chatService.chat(STUDENT_ID, request(501L, "问", false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_CONVERSATION_NOT_OWNED);
        verify(chatProvider, never()).chat(any(), any());
    }

    @Test
    void deletedConversationRejectedWith404() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, STUDENT_ID, 1));

        assertThatThrownBy(() -> chatService.chat(STUDENT_ID, request(501L, "问", false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_CONVERSATION_NOT_FOUND);
    }

    @Test
    void successWritesUserRowThenAssistantRowAndCountsQuota() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, STUDENT_ID, 0));
        when(messageMapper.selectList(any())).thenReturn(List.of(
                message(1L, "user", "上一问"), message(2L, "assistant", "上一答")));
        when(contextAssembler.assemble(eq(SYSTEM_PROMPT), any(), eq("本次问"))).thenAnswer(inv -> List.of(
                new ChatTurn("system", "s"),
                new ChatTurn("user", "上一问"),
                new ChatTurn("assistant", "上一答"),
                new ChatTurn("user", "本次问")));
        when(chatProvider.chat(any(), any())).thenReturn(
                new ChatResult("答案正文", "stop", 79, 291, 370, "Qwen/Qwen3.6-27B", 1200L));

        AiChatResponse response = chatService.chat(STUDENT_ID, request(501L, "本次问", false));

        // 审计顺序：user 行先落库，再调外部模型，再落 assistant 行
        InOrder inOrder = inOrder(messageMapper, chatProvider, quotaService);
        inOrder.verify(messageMapper).insert(any(AiMessageEntity.class));
        inOrder.verify(chatProvider).chat(any(), any());
        inOrder.verify(messageMapper).insert(any(AiMessageEntity.class));
        inOrder.verify(quotaService).recordUsage(STUDENT_ID, 370L);

        ArgumentCaptor<AiMessageEntity> assistantCaptor = ArgumentCaptor.forClass(AiMessageEntity.class);
        verify(messageMapper, times(2)).insert(assistantCaptor.capture());
        AiMessageEntity assistantRow = assistantCaptor.getAllValues().get(1);
        assertThat(assistantRow.getRole()).isEqualTo("assistant");
        assertThat(assistantRow.getContent()).isEqualTo("答案正文");
        assertThat(assistantRow.getStatus()).isEqualTo("OK");
        assertThat(assistantRow.getFinishReason()).isEqualTo("stop");
        assertThat(assistantRow.getPromptTokens()).isEqualTo(79);
        assertThat(assistantRow.getCompletionTokens()).isEqualTo(291);
        assertThat(assistantRow.getLatencyMs()).isEqualTo(1200);
        assertThat(response.getFinishReason()).isEqualTo("stop");
        assertThat(response.getUsage().totalTokens()).isEqualTo(370);
        // setUp 的消息插入回填 id：user=1001、assistant=1002；响应 messageId 必须是 assistant 行且字符串化
        assertThat(response.getMessageId()).isEqualTo("1002");
    }

    @Test
    void lengthFinishReasonMarksAssistantRowTruncated() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, STUDENT_ID, 0));
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(contextAssembler.assemble(anyString(), any(), anyString()))
                .thenReturn(List.of(new ChatTurn("system", "s"), new ChatTurn("user", "问")));
        when(chatProvider.chat(any(), any())).thenReturn(
                new ChatResult("", "length", 79, 64, 143, "Qwen/Qwen3.6-27B", 900L));

        AiChatResponse response = chatService.chat(STUDENT_ID, request(501L, "问", false));

        ArgumentCaptor<AiMessageEntity> captor = ArgumentCaptor.forClass(AiMessageEntity.class);
        verify(messageMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("TRUNCATED");
        assertThat(response.getFinishReason()).isEqualTo("length");
        verify(quotaService).recordUsage(eq(STUDENT_ID), eq(143L));
    }

    @Test
    void providerFailureStillWritesFailedAssistantRowThenThrows503() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, STUDENT_ID, 0));
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(contextAssembler.assemble(anyString(), any(), anyString()))
                .thenReturn(List.of(new ChatTurn("system", "s"), new ChatTurn("user", "问")));
        when(chatProvider.chat(any(), any())).thenThrow(
                new AiProviderException("AI upstream returned HTTP 503", 503, false, null));

        assertThatThrownBy(() -> chatService.chat(STUDENT_ID, request(501L, "问", false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_PROVIDER_UNAVAILABLE);

        ArgumentCaptor<AiMessageEntity> captor = ArgumentCaptor.forClass(AiMessageEntity.class);
        verify(messageMapper, times(2)).insert(captor.capture());
        AiMessageEntity failedRow = captor.getAllValues().get(1);
        assertThat(failedRow.getStatus()).isEqualTo("FAILED");
        assertThat(failedRow.getErrorCode()).isEqualTo("AI_PROVIDER_UNAVAILABLE");
        assertThat(failedRow.getFinishReason()).isEqualTo("error");
        assertThat(failedRow.getContent()).isEmpty();
        // 失败调用不计次
        verify(quotaService, never()).recordUsage(any(), anyLong());
    }

    @Test
    void historyPassedToAssemblerExcludesCurrentQuestionRow() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, STUDENT_ID, 0));
        when(messageMapper.selectList(any())).thenReturn(List.of(message(1L, "user", "上一问")));
        when(contextAssembler.assemble(eq(SYSTEM_PROMPT), any(), eq("本次问"))).thenReturn(List.of());
        when(chatProvider.chat(any(), any())).thenReturn(
                new ChatResult("答", "stop", 1, 1, 2, "m", 5L));

        chatService.chat(STUDENT_ID, request(501L, "本次问", false));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatTurn>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(contextAssembler).assemble(eq(SYSTEM_PROMPT), historyCaptor.capture(), eq("本次问"));
        assertThat(historyCaptor.getValue()).containsExactly(new ChatTurn("user", "上一问"));
    }

    private static AiMessageEntity message(Long id, String role, String content) {
        AiMessageEntity entity = new AiMessageEntity();
        entity.setId(id);
        entity.setConversationId(501L);
        entity.setRole(role);
        entity.setContent(content);
        entity.setStatus("OK");
        return entity;
    }
}
