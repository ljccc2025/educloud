package com.educloud.ai.provider;

import com.educloud.ai.chat.ChatTurn;
import com.educloud.ai.config.AiProperties;
// 最小修复（任务文本测试代码编译问题）：ChatOptions/ChatResult 是 ChatProvider 的嵌套类型，
// 同包简单名不可见，需显式 import；Spring 6.1 的 withStatus 只接受 HttpStatusCode，
// withStatus(429/400) 改为 HttpStatus 枚举（状态值语义不变）。
import com.educloud.ai.provider.ChatProvider.ChatOptions;
import com.educloud.ai.provider.ChatProvider.ChatResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleProviderTest {

    private static final String BASE_URL = "https://api.siliconflow.cn/v1";
    private static final String OK_BODY = """
            {
              "choices": [{
                "message": {"role": "assistant", "content": "第一步，明确定义。", "reasoning_content": "推理内容不应外泄"},
                "finish_reason": "stop"
              }],
              "usage": {"prompt_tokens": 79, "completion_tokens": 36, "total_tokens": 115}
            }
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private OpenAiCompatibleProvider provider;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OpenAiCompatibleProvider(builder, properties(false));
    }

    private static AiProperties properties(boolean thinkingEnabled) {
        return new AiProperties(
                new AiProperties.ProviderProperties("openai-compatible", BASE_URL, "Qwen/Qwen3.6-27B", "test-key", thinkingEnabled, 1024),
                new AiProperties.TimeoutProperties(5000, 25000),
                new AiProperties.QuotaProperties(50, 2000000),
                new AiProperties.ContextProperties(10, 3000),
                new AiProperties.JwtProperties("", "https://issuer.educloud.local", "educloud-api"));
    }

    @Test
    void chat_postsOpenAiCompatibleBodyWithThinkingDisabledAtTopLevel() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("Qwen/Qwen3.6-27B"))
                // 规格实测四条之一：顶层 enable_thinking:false（chat_template_kwargs 无效，不得出现）
                .andExpect(jsonPath("$.enable_thinking").value(false))
                .andExpect(jsonPath("$.chat_template_kwargs").doesNotExist())
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.max_tokens").value(1024))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("导数是什么？"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        ChatResult result = provider.chat(
                List.of(new ChatTurn("system", "你是助教"), new ChatTurn("user", "导数是什么？")),
                new ChatOptions(1024));

        server.verify();
        assertThat(result.content()).isEqualTo("第一步，明确定义。");
        assertThat(result.finishReason()).isEqualTo("stop");
        assertThat(result.promptTokens()).isEqualTo(79);
        assertThat(result.completionTokens()).isEqualTo(36);
        assertThat(result.totalTokens()).isEqualTo(115);
        assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void chat_returnsOnlyContentFieldAndNeverReasoning() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        ChatResult result = provider.chat(List.of(new ChatTurn("user", "问")), new ChatOptions(1024));

        assertThat(result.content()).isEqualTo("第一步，明确定义。");
        assertThat(result.content()).doesNotContain("推理内容");
    }

    @Test
    void chat_lengthFinishReasonPassedThroughEvenWhenContentEmpty() {
        // 实测四条之二：max_tokens 过小时 HTTP 200 且 content 为空，finish_reason=length 必须透传
        String truncated = """
                {
                  "choices": [{"message": {"role": "assistant", "content": ""}, "finish_reason": "length"}],
                  "usage": {"prompt_tokens": 79, "completion_tokens": 64, "total_tokens": 143}
                }
                """;
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(truncated, MediaType.APPLICATION_JSON));

        ChatResult result = provider.chat(List.of(new ChatTurn("user", "问")), new ChatOptions(1024));

        assertThat(result.content()).isEmpty();
        assertThat(result.finishReason()).isEqualTo("length");
    }

    @Test
    void chat_retriesOnceOnUpstream429ThenSucceeds() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        ChatResult result = provider.chat(List.of(new ChatTurn("user", "问")), new ChatOptions(1024));

        server.verify();
        assertThat(result.content()).isEqualTo("第一步，明确定义。");
    }

    @Test
    void chat_retriesOnceOnUpstream5xxThenSucceeds() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withServerError());
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        ChatResult result = provider.chat(List.of(new ChatTurn("user", "问")), new ChatOptions(1024));

        server.verify();
        assertThat(result.finishReason()).isEqualTo("stop");
    }

    @Test
    void chat_retriesOnceOnConnectFailureThenSucceeds() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withException(new ConnectException("Connection refused")));
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        ChatResult result = provider.chat(List.of(new ChatTurn("user", "问")), new ChatOptions(1024));

        server.verify();
        assertThat(result.content()).isEqualTo("第一步，明确定义。");
    }

    @Test
    void chat_givesUpAfterSecond429() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // 恰好 2 次尝试后放弃（AiProviderException 的 retryable 标记语义只控制内部重试循环，
        // 上层 ChatService 对任何 AiProviderException 都映射 503，因此这里只断言尝试次数）
        assertThatThrownBy(() -> provider.chat(List.of(new ChatTurn("user", "问")), new ChatOptions(1024)))
                .isInstanceOf(AiProviderException.class);

        server.verify();
    }

    @Test
    void chat_neverRetriesReadTimeout() {
        // 实测四条之三：超时=模型正在长时间生成，重试只会翻倍等待与成本
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> provider.chat(List.of(new ChatTurn("user", "问")), new ChatOptions(1024)))
                .isInstanceOf(AiProviderException.class)
                .extracting(e -> ((AiProviderException) e).retryable())
                .isEqualTo(false);

        server.verify();
    }

    @Test
    void chat_upstream400IsNotRetryable() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> provider.chat(List.of(new ChatTurn("user", "问")), new ChatOptions(1024)))
                .isInstanceOf(AiProviderException.class)
                .extracting(e -> ((AiProviderException) e).retryable())
                .isEqualTo(false);

        server.verify();
    }

    @Test
    void chat_whenThinkingEnabledOmitsTopLevelSwitch() {
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OpenAiCompatibleProvider(builder, properties(true));
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(jsonPath("$.enable_thinking").doesNotExist())
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        provider.chat(List.of(new ChatTurn("user", "问")), new ChatOptions(1024));

        server.verify();
    }
}
