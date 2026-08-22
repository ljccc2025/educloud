package com.educloud.user.support;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.user.config.FileClientProperties;
import com.educloud.user.service.ServiceTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File 服务内部 HTTP 客户端（M04 任务 14）。
 *
 * <p>以 user-service 服务令牌（aud=educloud-file, scope=file:internal）调用 File
 * /internal/v1/**：bind/unbind 头像绑定，批量 grant 组装 avatarUrl。令牌缓存至过期前
 * 30s；非 2xx 与传输异常统一抛 DEPENDENCY_UNAVAILABLE；enabled=false 时全部 no-op，
 * 保证本地无 File 服务时 User 可用。</p>
 */
@Component
public class FileClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileClient.class);
    private static final String AUDIENCE = "educloud-file";
    private static final List<String> SCOPES = List.of("file:internal");
    private static final String OWNER_TYPE = "USER_PROFILE";
    private static final String SUBJECT_TYPE = "USER";
    private static final String GRANT_PURPOSE = "PROFILE_AVATAR";
    private static final int GRANT_BATCH_LIMIT = 100;
    private static final long TOKEN_REFRESH_LEAD_SECONDS = 30L;

    private final FileClientProperties properties;
    private final ServiceTokenService serviceTokenService;
    private final RestClient restClient;
    private final Clock clock;
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    @Autowired
    public FileClient(
            FileClientProperties properties,
            ServiceTokenService serviceTokenService,
            RestClient.Builder restClientBuilder,
            Clock clock) {
        this(properties, serviceTokenService, buildRestClient(restClientBuilder, properties), clock);
    }

    /** 测试构造：直接注入已绑定 MockRestServiceServer 的 RestClient。 */
    FileClient(
            FileClientProperties properties,
            ServiceTokenService serviceTokenService,
            RestClient restClient,
            Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.serviceTokenService = Objects.requireNonNull(serviceTokenService, "serviceTokenService");
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static RestClient buildRestClient(RestClient.Builder builder, FileClientProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.timeout().toMillis());
        requestFactory.setReadTimeout((int) properties.timeout().toMillis());
        return builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(properties.endpoint())
                .build();
    }

    /** 绑定头像文件到 USER_PROFILE 属主（POST /internal/v1/files/{fileId}/bind）。 */
    public void bindAvatar(Long userId, Long fileId) {
        if (!properties.enabled()) {
            return;
        }
        Map<String, String> body = Map.of(
                "ownerType", OWNER_TYPE,
                "ownerId", String.valueOf(userId));
        postJson("/internal/v1/files/{id}/bind", body, fileId);
    }

    /** 解绑头像文件与 USER_PROFILE 属主（POST /internal/v1/files/{fileId}/unbind）。 */
    public void unbindAvatar(Long userId, Long fileId) {
        if (!properties.enabled()) {
            return;
        }
        Map<String, String> body = Map.of(
                "ownerType", OWNER_TYPE,
                "ownerId", String.valueOf(userId));
        postJson("/internal/v1/files/{id}/unbind", body, fileId);
    }

    /**
     * 本人头像批量授权：items 的 ownerId 均为 subjectUserId（/me 场景）。
     * 返回仅含 GRANTED 项的 fileId→url 映射。
     */
    public Map<Long, String> grantAvatarUrls(List<Long> fileIds, Long subjectUserId) {
        if (!properties.enabled() || fileIds == null || fileIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> ownerUserIdByFileId = new HashMap<>();
        for (Long fileId : fileIds.stream().distinct().toList()) {
            ownerUserIdByFileId.put(fileId, subjectUserId);
        }
        return grantAvatarUrls(fileIds.stream().distinct().toList(), subjectUserId, ownerUserIdByFileId);
    }

    /**
     * 管理端多用户批量授权：每个 item 的 ownerId 来自 ownerUserIdByFileId，subject 为
     * 当前管理员（管理端详情/分页场景）。≤100 分批，单次仅一个 HTTP 调用。
     */
    public Map<Long, String> grantAvatarUrls(
            List<Long> fileIds, Long subjectUserId, Map<Long, Long> ownerUserIdByFileId) {
        if (!properties.enabled() || fileIds == null || fileIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinctFileIds = fileIds.stream().distinct().toList();
        Map<Long, String> urls = new HashMap<>();
        for (List<Long> chunk : partition(distinctFileIds, GRANT_BATCH_LIMIT)) {
            List<GrantItem> items = chunk.stream()
                    .map(fileId -> new GrantItem(
                            String.valueOf(fileId),
                            fileId,
                            OWNER_TYPE,
                            String.valueOf(ownerUserIdByFileId.get(fileId))))
                    .toList();
            GrantRequest request = new GrantRequest(
                    SUBJECT_TYPE, subjectUserId, GRANT_PURPOSE, null, items);
            GrantResponse response = postForObject(
                    "/internal/v1/file-download-grants/batch", request, GrantResponse.class);
            for (GrantItemResult item : response.items()) {
                if ("GRANTED".equals(item.status()) && item.url() != null) {
                    urls.put(item.fileId(), item.url());
                }
            }
        }
        return urls;
    }

    private void postJson(String uriTemplate, Object body, Object... uriVariables) {
        try {
            restClient.post()
                    .uri(uriTemplate, uriVariables)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw dependencyUnavailable(uriTemplate, response.getStatusCode().value());
                    })
                    .toBodilessEntity();
        } catch (BusinessException failure) {
            throw failure;
        } catch (RestClientException failure) {
            throw dependencyUnavailable(uriTemplate, failure);
        }
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
                        throw dependencyUnavailable(uriTemplate, response.getStatusCode().value());
                    })
                    .body(responseType);
        } catch (BusinessException failure) {
            throw failure;
        } catch (RestClientException failure) {
            throw dependencyUnavailable(uriTemplate, failure);
        }
    }

    private String bearerToken() {
        CachedToken cached = tokenCache.get(properties.clientId());
        if (cached == null || clock.instant().plusSeconds(TOKEN_REFRESH_LEAD_SECONDS).isAfter(cached.expiresAt())) {
            ServiceTokenService.IssueResult issued = serviceTokenService.issue(
                    properties.clientId(),
                    properties.clientSecret(),
                    AUDIENCE,
                    SCOPES,
                    null,
                    null);
            cached = new CachedToken(issued.accessToken(), clock.instant().plusSeconds(issued.expiresIn()));
            tokenCache.put(properties.clientId(), cached);
        }
        return cached.accessToken();
    }

    private BusinessException dependencyUnavailable(String uri, int status) {
        LOGGER.warn("File 内部接口失败: uri={}, httpStatus={}", uri, status);
        return new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                "File service unavailable: " + uri + " (HTTP " + status + ")");
    }

    private BusinessException dependencyUnavailable(String uri, RestClientException failure) {
        LOGGER.warn("File 内部接口调用异常: uri={}", uri, failure);
        return new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                "File service unavailable: " + uri, null, failure);
    }

    private static List<List<Long>> partition(List<Long> values, int size) {
        List<List<Long>> chunks = new ArrayList<>();
        for (int index = 0; index < values.size(); index += size) {
            chunks.add(values.subList(index, Math.min(values.size(), index + size)));
        }
        return chunks;
    }

    /** 批量授权请求体（与 File BatchDownloadGrantRequest 同构）。 */
    private record GrantRequest(
            String subjectType,
            Long subjectUserId,
            String purpose,
            Long requestedTtlSeconds,
            List<GrantItem> items) {
    }

    private record GrantItem(String requestKey, Long fileId, String ownerType, String ownerId) {
    }

    /** 批量授权响应体（与 File BatchGrantResult 同构；expiresAt 仅透传不解析）。 */
    private record GrantResponse(List<GrantItemResult> items) {
    }

    private record GrantItemResult(
            String requestKey, Long fileId, String status, String url, String expiresAt) {
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }
}