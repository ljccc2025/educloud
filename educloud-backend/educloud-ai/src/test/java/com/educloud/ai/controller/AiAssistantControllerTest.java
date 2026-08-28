package com.educloud.ai.controller;

import com.educloud.ai.chat.ChatService;
import com.educloud.ai.entity.AiConversationEntity;
import com.educloud.ai.exception.AiErrorCode;
import com.educloud.ai.mapper.AiConversationMapper;
import com.educloud.ai.mapper.AiMessageMapper;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.BusinessException;
import com.educloud.common.web.RequestContextAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantControllerTest {

    private static final Long STUDENT_ID = 2001L;

    @Mock
    private ChatService chatService;
    @Mock
    private AiConversationMapper conversationMapper;
    @Mock
    private AiMessageMapper messageMapper;

    @InjectMocks
    private AiAssistantController controller;

    private static Jwt jwt(Long userId, String... roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(String.valueOf(userId))
                .claim("roles", List.of(roles))
                .build();
    }

    private static AiConversationEntity conversation(Long id, Long studentId, int deleted) {
        AiConversationEntity entity = new AiConversationEntity();
        entity.setId(id);
        entity.setStudentId(studentId);
        entity.setTitle("标题");
        entity.setMessageCount(2);
        entity.setDeleted(deleted);
        return entity;
    }

    @BeforeEach
    void setUp() {
        // @InjectMocks 不处理 ApiResponseFactory 手动依赖，直接注入
        controller = new AiAssistantController(chatService, conversationMapper, messageMapper,
                new ApiResponseFactory(new RequestContextAccessor() {
                    @Override
                    public String requestId() {
                        return "test-request-id";
                    }

                    @Override
                    public java.util.Optional<String> traceId() {
                        return java.util.Optional.empty();
                    }
                }, Clock.systemUTC()));
    }

    @Test
    void messagesOfForeignConversationRejectedWith403() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, 9999L, 0));

        assertThatThrownBy(() -> controller.listMessages(501L, jwt(STUDENT_ID, "STUDENT")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_CONVERSATION_NOT_OWNED);
    }

    @Test
    void messagesOfDeletedConversationRejectedWith404() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, STUDENT_ID, 1));

        assertThatThrownBy(() -> controller.listMessages(501L, jwt(STUDENT_ID, "STUDENT")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_CONVERSATION_NOT_FOUND);
    }

    @Test
    void deleteOfForeignConversationRejectedWith403() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, 9999L, 0));

        assertThatThrownBy(() -> controller.deleteConversation(501L, jwt(STUDENT_ID, "STUDENT")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_CONVERSATION_NOT_OWNED);
    }

    @Test
    void deleteOfOwnConversationReturns204AndSoftDeletes() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, STUDENT_ID, 0));

        assertThat(controller.deleteConversation(501L, jwt(STUDENT_ID, "STUDENT")).getStatusCode().value())
                .isEqualTo(204);
        // 最小修复（MP 3.5.12 编译问题）：BaseMapper 有 updateById(T) 与 updateById(Collection<T>)
        // 两个重载，裸 any() 二义性，需指明实体类型（ChatServiceTest 的 insert(any()) 同款修复）。
        org.mockito.ArgumentCaptor<AiConversationEntity> captor =
                org.mockito.ArgumentCaptor.forClass(AiConversationEntity.class);
        org.mockito.Mockito.verify(conversationMapper).updateById(captor.capture());
        // 软删语义必须锁定：deleted 置 1 且 updatedAt 回写，而非物理 DELETE
        assertThat(captor.getValue().getDeleted()).isEqualTo(1);
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void nonStudentRoleRejectedWith403() {
        assertThatThrownBy(() -> controller.listConversations(1, 20, jwt(STUDENT_ID, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_ACCESS_DENIED);
    }
}
