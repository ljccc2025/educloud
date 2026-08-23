package com.educloud.course.service;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.course.config.CourseFileProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Course 服务令牌签发客户端（M05 任务 12）。
 *
 * <p>Course 无法自行签发 File 可验签的服务令牌（File 用 User 公钥 JWKS 验签），
 * 按 M03 设计规格第 8 节以 HTTP Basic client_credentials 调 User
 * {@code POST /internal/v1/service-tokens}（form：grant_type/audience/scope），
 * 换取 5 分钟服务令牌。非 2xx 与传输异常统一映射 {@link CommonErrorCode#DEPENDENCY_UNAVAILABLE}，
 * 由 {@link FileClient} 缓存令牌（提前 30s 刷新）。</p>
 *
 * <p>依赖项：User 模块需实现该端点（M03 规格 §8 已定义；当前 user 服务尚未暴露，
 * 联调前需补齐——见任务 12 报告疑虑）。</p>
 */
@Service
public final class ServiceTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceTokenService.class);
    private static final String TOKEN_PATH = "/internal/v1/service-tokens";

    private final CourseFileProperties properties;
    private final RestClient restClient;

    @Autowired
    public ServiceTokenService(RestClient.Builder restClientBuilder, CourseFileProperties properties) {
        this(properties, buildRestClient(restClientBuilder, properties));
    }

    /** 测试构造：直接注入已绑定 MockRestServiceServer 的 RestClient。 */
    ServiceTokenService(CourseFileProperties properties, RestClient restClient) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.restClient = Objects.requireNonNull(restClient, "restClient");
    }

    private static RestClient buildRestClient(RestClient.Builder builder, CourseFileProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.timeout().toMillis());
        requestFactory.setReadTimeout((int) properties.timeout().toMillis());
        return builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(properties.tokenEndpoint())
                .build();
    }

    /** 签发服务令牌：HTTP Basic client_credentials（audience + scope 表单参数）。 */
    public IssueResult issue(
            String clientId, String clientSecret, String audience, List<String> scopes,
            String ip, String requestId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("audience", audience);
        form.add("scope", String.join(" ", scopes));
        try {
            TokenResponse response = restClient.post()
                    .uri(TOKEN_PATH)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth(clientId, clientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                        "User service-tokens response is empty: " + TOKEN_PATH);
            }
            return new IssueResult(response.accessToken(), response.expiresIn());
        } catch (BusinessException failure) {
            throw failure;
        } catch (RestClientException failure) {
            LOGGER.warn("User service-tokens 调用失败: path={}", TOKEN_PATH, failure);
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                    "User service-tokens unavailable: " + TOKEN_PATH, null, failure);
        }
    }

    private static String basicAuth(String clientId, String clientSecret) {
        String raw = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 令牌响应体（与 M03 规格 §8 {access_token, token_type, expires_in} 同构）。 */
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn) {
    }

    public record IssueResult(String accessToken, long expiresIn) {
    }
}
