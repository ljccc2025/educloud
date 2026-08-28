package com.educloud.ai.provider;

import com.educloud.ai.chat.ChatTurn;
import com.educloud.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议实现（硅基流动实测约束，规格 §5.2）：
 * 1. 顶层 enable_thinking:false 关思考（chat_template_kwargs 实测无效，绝不发送）；
 * 2. 只取 choices[0].message.content 作为答案，reasoning_content 一律不读不存不返；
 * 3. 不能只看 HTTP 状态码：finish_reason=length 时 content 可能为空，原样透传给上层判 TRUNCATED；
 * 4. 超时不重试；连接失败与上游 429/5xx 重试 1 次，退避 1s。
 * 日志只记状态/finish_reason/usage/延迟，绝不打印 api-key 与请求体。
 */
@Slf4j
public class OpenAiCompatibleProvider implements ChatProvider {

    private static final int MAX_ATTEMPTS = 2;
    private static final long RETRY_BACKOFF_MS = 1000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final AiProperties properties;

    public OpenAiCompatibleProvider(RestClient.Builder builder, AiProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    @Override
    public ChatResult chat(List<ChatTurn> messages, ChatOptions options) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return execute(messages, options);
            } catch (AiProviderException exception) {
                if (!exception.retryable() || attempts >= MAX_ATTEMPTS) {
                    throw exception;
                }
                log.warn("AI upstream retryable failure (status={}), retrying in {} ms",
                        exception.upstreamStatus(), RETRY_BACKOFF_MS);
                sleepBeforeRetry();
            }
        }
    }

    private ChatResult execute(List<ChatTurn> messages, ChatOptions options) {
        AiProperties.ProviderProperties provider = properties.provider();
        long startedAt = System.currentTimeMillis();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", provider.model());
        body.put("messages", toMessageList(messages));
        body.put("stream", false);
        body.put("max_tokens", options.maxTokens());
        if (!provider.thinkingEnabled()) {
            body.put("enable_thinking", false);
        }

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(provider.baseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + provider.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            boolean retryable = status == 429 || status >= 500;
            log.warn("AI upstream HTTP error: status={}, latencyMs={}, retryable={}",
                    status, System.currentTimeMillis() - startedAt, retryable);
            throw new AiProviderException("AI upstream returned HTTP " + status, status, retryable, exception);
        } catch (ResourceAccessException exception) {
            boolean retryable = isConnectFailure(exception);
            log.warn("AI upstream access failure: type={}, latencyMs={}, retryable={}",
                    exception.getCause() == null ? "unknown" : exception.getCause().getClass().getSimpleName(),
                    System.currentTimeMillis() - startedAt, retryable);
            throw new AiProviderException("AI upstream is unreachable", 0, retryable, exception);
        }

        long latencyMs = System.currentTimeMillis() - startedAt;
        return parse(responseBody, provider.model(), latencyMs);
    }

    private ChatResult parse(String responseBody, String model, long latencyMs) {
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(responseBody == null ? "" : responseBody);
        } catch (Exception exception) {
            throw new AiProviderException("AI upstream returned invalid JSON", 0, false, exception);
        }
        JsonNode choice = root.path("choices").path(0);
        String content = choice.path("message").path("content").asText("");
        // reasoning_content 有意不读取：只把 content 作为答案落库与返回（规格 §5.1）
        String finishReason = choice.path("finish_reason").asText("");
        int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
        int completionTokens = root.path("usage").path("completion_tokens").asInt(0);
        int totalTokens = root.path("usage").path("total_tokens").asInt(promptTokens + completionTokens);
        log.info("AI upstream answered: finishReason={}, promptTokens={}, completionTokens={}, latencyMs={}",
                finishReason, promptTokens, completionTokens, latencyMs);
        return new ChatResult(content, finishReason, promptTokens, completionTokens, totalTokens, model, latencyMs);
    }

    private List<Map<String, String>> toMessageList(List<ChatTurn> messages) {
        List<Map<String, String>> list = new ArrayList<>();
        for (ChatTurn turn : messages) {
            list.add(Map.of("role", turn.role(), "content", turn.content()));
        }
        return list;
    }

    private boolean isConnectFailure(ResourceAccessException exception) {
        Throwable cause = exception.getCause();
        return cause instanceof ConnectException
                || cause instanceof UnknownHostException;
        // SocketTimeoutException（含 connect/read 超时）一律视为超时：不重试
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Interrupted while backing off before AI retry", 0, false, exception);
        }
    }
}
