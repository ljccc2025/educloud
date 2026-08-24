package com.educloud.content.service;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.content.config.ContentFileProperties;
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

@Service
public final class ServiceTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceTokenService.class);
    private static final String TOKEN_PATH = "/internal/v1/service-tokens";

    private final ContentFileProperties properties;
    private final RestClient restClient;

    @Autowired
    public ServiceTokenService(RestClient.Builder restClientBuilder, ContentFileProperties properties) {
        this(properties, buildRestClient(restClientBuilder, properties));
    }

    ServiceTokenService(ContentFileProperties properties, RestClient restClient) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.restClient = Objects.requireNonNull(restClient, "restClient");
    }

    private static RestClient buildRestClient(RestClient.Builder builder, ContentFileProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        if (properties.timeout() != null) {
            requestFactory.setConnectTimeout((int) properties.timeout().toMillis());
            requestFactory.setReadTimeout((int) properties.timeout().toMillis());
        }
        return builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(properties.tokenEndpoint() != null ? properties.tokenEndpoint() : "http://127.0.0.1:8082")
                .build();
    }

    public IssueResult issue(
            String clientId, String clientSecret, String audience, List<String> scopes) {
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
        String raw = (clientId != null ? clientId : "") + ":" + (clientSecret != null ? clientSecret : "");
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn) {
    }

    public record IssueResult(String accessToken, long expiresIn) {
    }
}
