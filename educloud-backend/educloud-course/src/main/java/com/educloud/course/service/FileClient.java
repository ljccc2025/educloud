package com.educloud.course.service;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.course.config.CourseFileProperties;
import com.educloud.course.exception.CourseErrorCode;
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
 * File 服务内部 HTTP 客户端（M05 任务 12）。
 *
 * <p>以 course-service 服务令牌（aud=educloud-file, scope=file:internal）调用 File
 * /internal/v1/**：bind/unbind 课程封面（ownerType=COURSE、ownerId=courseId，
 * bind 携带 uploaderUserId=当前教师，File 侧校验 file_object.uploader_id 属主），
 * 批量 grant 组装 coverUrl（列表/详情公开封面 ANONYMOUS + PUBLIC_CATALOG，教师草稿
 * USER subject）。令牌缓存至过期前 30s；403→COURSE_ACCESS_DENIED、404→COURSE_NOT_FOUND、
 * 其余非 2xx 与传输异常统一抛 DEPENDENCY_UNAVAILABLE；enabled=false 时全部 no-op，
 * 保证本地无 File 服务时 Course 可用。</p>
 */
@Component
public class FileClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileClient.class);
    private static final String AUDIENCE = "educloud-file";
    private static final List<String> SCOPES = List.of("file:internal");
    private static final String OWNER_TYPE = "COURSE";
    private static final String GRANT_PURPOSE = "PUBLIC_CATALOG";
    private static final String SUBJECT_ANONYMOUS = "ANONYMOUS";
    private static final String SUBJECT_USER = "USER";
    private static final int GRANT_BATCH_LIMIT = 100;
    private static final long TOKEN_REFRESH_LEAD_SECONDS = 30L;

    private final CourseFileProperties properties;
    private final ServiceTokenService serviceTokenService;
    private final RestClient restClient;
    private final Clock clock;
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    @Autowired
    public FileClient(
            CourseFileProperties properties,
            ServiceTokenService serviceTokenService,
            RestClient.Builder restClientBuilder,
            Clock clock) {
        this(properties, serviceTokenService, buildRestClient(restClientBuilder, properties), clock);
    }

    /** 测试构造：直接注入已绑定 MockRestServiceServer 的 RestClient。 */
    FileClient(
            CourseFileProperties properties,
            ServiceTokenService serviceTokenService,
            RestClient restClient,
            Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.serviceTokenService = Objects.requireNonNull(serviceTokenService, "serviceTokenService");
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static RestClient buildRestClient(RestClient.Builder builder, CourseFileProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.timeout().toMillis());
        requestFactory.setReadTimeout((int) properties.timeout().toMillis());
        return builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(properties.endpoint())
                .build();
    }

    /**
     * 绑定封面文件到 COURSE 属主（POST /internal/v1/files/{fileId}/bind）。
     *
     * <p>uploaderUserId=当前教师：File 侧对照 file_object.uploader_id 校验上传者属主
     * （规格 §9 信任边界），他人 fileId → 403 COURSE_ACCESS_DENIED。</p>
     */
    public void bindCover(Long courseId, Long fileId, Long uploaderUserId) {
        if (!properties.enabled()) {
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("ownerType", OWNER_TYPE);
        body.put("ownerId", String.valueOf(courseId));
        body.put("uploaderUserId", uploaderUserId);
        postJson("/internal/v1/files/{id}/bind", body, fileId);
    }

    /** 解绑封面文件与 COURSE 属主（POST /internal/v1/files/{fileId}/unbind，幂等）。 */
    public void unbindCover(Long courseId, Long fileId) {
        if (!properties.enabled()) {
            return;
        }
        Map<String, String> body = Map.of(
                "ownerType", OWNER_TYPE,
                "ownerId", String.valueOf(courseId));
        postJson("/internal/v1/files/{id}/unbind", body, fileId);
    }

    /** 已发布课程封面批量授权：ANONYMOUS + PUBLIC_CATALOG（匿名公开列表/详情）。 */
    public Map<Long, String> grantPublicCatalogUrls(Map<Long, Long> ownerCourseIdByFileId) {
        return grantCatalogUrls(ownerCourseIdByFileId, null);
    }

    /**
     * 教师可见封面批量授权：subject=USER（subjectUserId=教师本人），purpose=PUBLIC_CATALOG；
     * subjectUserId 为 null 时走 ANONYMOUS（公开课程）。≤100 分批，单次仅一个 HTTP 调用。
     */
    public Map<Long, String> grantCatalogUrls(Map<Long, Long> ownerCourseIdByFileId, Long subjectUserId) {
        if (!properties.enabled() || ownerCourseIdByFileId == null || ownerCourseIdByFileId.isEmpty()) {
            return Map.of();
        }
        String subjectType = subjectUserId == null ? SUBJECT_ANONYMOUS : SUBJECT_USER;
        Map<Long, String> urls = new HashMap<>();
        List<Long> fileIds = ownerCourseIdByFileId.keySet().stream().distinct().toList();
        for (List<Long> chunk : partition(fileIds, GRANT_BATCH_LIMIT)) {
            List<GrantItem> items = chunk.stream()
                    .map(fileId -> new GrantItem(
                            String.valueOf(fileId),
                            fileId,
                            OWNER_TYPE,
                            String.valueOf(ownerCourseIdByFileId.get(fileId))))
                    .toList();
            GrantRequest request = new GrantRequest(
                    subjectType, subjectUserId, GRANT_PURPOSE, null, items);
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
                        throw mapError(uriTemplate, response.getStatusCode().value());
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
                        throw mapError(uriTemplate, response.getStatusCode().value());
                    })
                    .body(responseType);
        } catch (BusinessException failure) {
            throw failure;
        } catch (RestClientException failure) {
            throw dependencyUnavailable(uriTemplate, failure);
        }
    }

    /**
     * 下游 4xx 语义化透传（复刻 user 版 M04 审查修复）：403=越权/伪造
     * （COURSE_ACCESS_DENIED）、404=文件不存在（COURSE_NOT_FOUND），其余错误与传输异常
     * 仍视为依赖不可用（503），避免业务拒绝被误判为 File 服务故障。
     */
    private BusinessException mapError(String uri, int status) {
        if (status == 403) {
            return new BusinessException(CourseErrorCode.COURSE_ACCESS_DENIED,
                    "File 拒绝访问: " + uri + " (HTTP 403)");
        }
        if (status == 404) {
            return new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "File 对象不存在: " + uri + " (HTTP 404)");
        }
        return dependencyUnavailable(uri, status);
    }

    /**
     * 返回缓存的 Bearer 令牌；缓存缺失或临近过期（30s 提前量）时签发新令牌。
     * synchronized(tokenCache) + 双重检查，避免并发首次签发竞态（复刻 user 版 B2 修复）。
     */
    private String bearerToken() {
        String clientId = properties.clientId();
        CachedToken cached = tokenCache.get(clientId);
        if (cached == null || isExpiringSoon(cached)) {
            synchronized (tokenCache) {
                cached = tokenCache.get(clientId);
                if (cached == null || isExpiringSoon(cached)) {
                    ServiceTokenService.IssueResult issued = serviceTokenService.issue(
                            clientId,
                            properties.clientSecret(),
                            AUDIENCE,
                            SCOPES,
                            null,
                            null);
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
