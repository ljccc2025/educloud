package com.educloud.content.service;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.content.config.ContentFileProperties;
import com.educloud.content.exception.ContentErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FileClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileClient.class);
    private static final String AUDIENCE = "educloud-file";
    private static final List<String> SCOPES = List.of("file:internal");
    private static final String OWNER_TYPE = "COURSEWARE";
    private static final String GRANT_PURPOSE = "COURSE_STREAMING";
    private static final String SUBJECT_USER = "USER";
    private static final String SUBJECT_ANONYMOUS = "ANONYMOUS";
    private static final long TOKEN_REFRESH_LEAD_SECONDS = 30L;

    private final ContentFileProperties properties;
    private final ServiceTokenService serviceTokenService;
    private final RestClient restClient;
    private final Clock clock;
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    @Autowired
    public FileClient(
            ContentFileProperties properties,
            ServiceTokenService serviceTokenService,
            RestClient.Builder restClientBuilder,
            Clock clock) {
        this(properties, serviceTokenService, buildRestClient(restClientBuilder, properties), clock);
    }

    FileClient(
            ContentFileProperties properties,
            ServiceTokenService serviceTokenService,
            RestClient restClient,
            Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.serviceTokenService = Objects.requireNonNull(serviceTokenService, "serviceTokenService");
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static RestClient buildRestClient(RestClient.Builder builder, ContentFileProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        if (properties.timeout() != null) {
            requestFactory.setConnectTimeout((int) properties.timeout().toMillis());
            requestFactory.setReadTimeout((int) properties.timeout().toMillis());
        }
        return builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(properties.endpoint() != null ? properties.endpoint() : "http://127.0.0.1:8087")
                .build();
    }

    public String getDownloadUrl(Long fileId, Long coursewareId, Long userId) {
        if (!properties.enabled()) {
            return "http://127.0.0.1:9000/educloud-files/mock-content-" + fileId + ".mp4";
        }

        Map<String, Object> body = new HashMap<>();
        body.put("subjectType", userId != null ? SUBJECT_USER : SUBJECT_ANONYMOUS);
        body.put("subjectUserId", userId);
        body.put("ownerType", OWNER_TYPE);
        body.put("ownerId", String.valueOf(coursewareId));
        body.put("purpose", GRANT_PURPOSE);
        body.put("requestedTtlSeconds", 900L); // 15 mins

        GrantResult result = postForObject("/internal/v1/files/{id}/download-grants", body, GrantResult.class, fileId);
        if (result != null && result.url() != null) {
            return result.url();
        }
        return null;
    }

    private <T> T postForObject(
            String uriTemplate, Object body, Class<T> responseType, Object... uriVariables) {
        try {
            return restClient.post()
                    .uri(uriTemplate, uriVariables)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        int code = response.getStatusCode().value();
                        if (code == 403) {
                            throw new BusinessException(ContentErrorCode.COURSEWARE_ACCESS_DENIED, "File download grant denied");
                        }
                        if (code == 404) {
                            throw new BusinessException(ContentErrorCode.COURSEWARE_NOT_FOUND, "File not found: " + uriTemplate);
                        }
                        throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE, "File service returned " + code);
                    })
                    .body(responseType);
        } catch (BusinessException failure) {
            throw failure;
        } catch (RestClientException failure) {
            LOGGER.warn("File service error on uri {}: {}", uriTemplate, failure.getMessage());
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE, "File service unavailable", null, failure);
        }
    }

    private String bearerToken() {
        String clientId = properties.clientId() != null ? properties.clientId() : "educloud-content";
        CachedToken cached = tokenCache.get(clientId);
        if (cached == null || isExpiringSoon(cached)) {
            synchronized (tokenCache) {
                cached = tokenCache.get(clientId);
                if (cached == null || isExpiringSoon(cached)) {
                    ServiceTokenService.IssueResult issued = serviceTokenService.issue(
                            clientId,
                            properties.clientSecret(),
                            AUDIENCE,
                            SCOPES);
                    cached = new CachedToken(issued.accessToken(), clock.instant().plusSeconds(issued.expiresIn()));
                    tokenCache.put(clientId, cached);
                }
            }
        }
        return cached.accessToken();
    }

    private boolean isExpiringSoon(CachedToken cached) {
        return clock.instant().plusSeconds(TOKEN_REFRESH_LEAD_SECONDS).isAfter(cached.expiresAt());
    }

    private record GrantResult(String status, String url, String expiresAt) {
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }
}
