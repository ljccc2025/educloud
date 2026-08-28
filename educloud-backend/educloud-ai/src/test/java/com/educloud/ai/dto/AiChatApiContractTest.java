package com.educloud.ai.dto;

import com.educloud.ai.dto.response.AiChatResponse;
import com.educloud.ai.dto.response.AiUsageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * /api/v1/ai/chat 响应字段契约（规格 2026-08-28-ai-assistant-p1-design.md §4/§7）。
 * 锁三条对外约定：字段名与嵌套结构固定；雪花 ID 一律字符串化（JS 精度）；
 * degraded 字段存在且为布尔（P1 恒 false，P2 复用）。
 */
class AiChatApiContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void chatResponse_contract_fieldNamesTypesAndStringIds() throws Exception {
        AiChatResponse response = AiChatResponse.builder()
                .conversationId("1943234567890123456")
                .messageId("1943234567890123457")
                .content("第一步，明确极限的定义……")
                .finishReason("stop")
                .usage(new AiUsageResponse(79, 291, 370))
                .degraded(false)
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "conversationId", "messageId", "content", "finishReason", "usage", "degraded");
        assertThat(json.get("conversationId").isTextual()).isTrue();
        assertThat(json.get("messageId").isTextual()).isTrue();
        assertThat(json.get("content").isTextual()).isTrue();
        assertThat(json.get("finishReason").isTextual()).isTrue();
        assertThat(json.get("degraded").isBoolean()).isTrue();
        JsonNode usage = json.get("usage");
        assertThat(usage.fieldNames()).toIterable().containsExactly(
                "promptTokens", "completionTokens", "totalTokens");
        assertThat(usage.get("promptTokens").asInt()).isEqualTo(79);
        assertThat(usage.get("completionTokens").asInt()).isEqualTo(291);
        assertThat(usage.get("totalTokens").asInt()).isEqualTo(370);
    }
}
