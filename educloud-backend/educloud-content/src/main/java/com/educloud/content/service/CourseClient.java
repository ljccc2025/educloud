package com.educloud.content.service;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.content.config.ContentCourseProperties;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * course 服务内部客户端（BUG-002 修复）：以服务令牌（aud=educloud-course）调用
 * GET /internal/v1/courses/{courseId}/enrollments/{studentId} 查询学员报名状态，
 * 作为付费/免费课件下载准入的权益权威来源（免费课程走 enroll 报名、付费课程由
 * order 已支付事件开课，均在 course_enrollment 落行）。
 *
 * <p>参照 {@link FileClient} 模式：token 端点/签发复用 ServiceTokenService，
 * 令牌缓存按 clientId（实例级，与 FileClient 缓存互不共享——aud 不同）。
 * course 服务不可用时抛 DEPENDENCY_UNAVAILABLE（fail-closed：报名校验失败
 * 不得放行付费内容下载）。</p>
 */
@Component
public class CourseClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(CourseClient.class);
    private static final String AUDIENCE = "educloud-course";
    private static final List<String> SCOPES = List.of("course:internal");
    private static final long TOKEN_REFRESH_LEAD_SECONDS = 30L;

    private final ContentCourseProperties properties;
    private final ServiceTokenService serviceTokenService;
    private final RestClient restClient;
    private final Clock clock;
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    @Autowired
    public CourseClient(
            ContentCourseProperties properties,
            ServiceTokenService serviceTokenService,
            RestClient.Builder restClientBuilder,
            Clock clock) {
        this(properties, serviceTokenService, buildRestClient(restClientBuilder, properties), clock);
    }

    CourseClient(
            ContentCourseProperties properties,
            ServiceTokenService serviceTokenService,
            RestClient restClient,
            Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.serviceTokenService = Objects.requireNonNull(serviceTokenService, "serviceTokenService");
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static RestClient buildRestClient(RestClient.Builder builder, ContentCourseProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        if (properties.timeout() != null) {
            requestFactory.setConnectTimeout((int) properties.timeout().toMillis());
            requestFactory.setReadTimeout((int) properties.timeout().toMillis());
        }
        return builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(properties.endpoint() != null ? properties.endpoint() : "http://127.0.0.1:8089")
                .build();
    }

    /**
     * 学员是否对该课程持有有效报名（ACTIVE）。enabled=false（本地开发未起
     * course 服务）时跳过校验放行并打 WARN——生产必须保持 enabled=true。
     */
    public boolean isEnrolled(Long courseId, Long studentId) {
        if (!properties.enabled()) {
            LOGGER.warn("Course client disabled, skipping enrollment check for courseware access "
                    + "(DEV ONLY): courseId={}, studentId={}", courseId, studentId);
            return true;
        }

        EnrollmentStatus status = getForObject(
                "/internal/v1/courses/{courseId}/enrollments/{studentId}",
                EnrollmentStatus.class, courseId, studentId);
        return status != null && status.enrolled();
    }

    /**
     * 教师是否归属该课程（OWNER 或 course_teacher 成员，BUG-005 修复：教师端
     * 内容操作的横向越权防护）。enabled=false 时跳过校验放行（DEV ONLY）。
     */
    public boolean isCourseTeacher(Long courseId, Long teacherId) {
        if (!properties.enabled()) {
            LOGGER.warn("Course client disabled, skipping course ownership check "
                    + "(DEV ONLY): courseId={}, teacherId={}", courseId, teacherId);
            return true;
        }

        CourseAccess access = getForObject("/internal/v1/courses/{courseId}", CourseAccess.class, courseId);
        if (access == null) {
            return false;
        }
        String teacherIdText = String.valueOf(teacherId);
        if (teacherIdText.equals(access.ownerTeacherId())) {
            return true;
        }
        return access.teachers() != null && access.teachers().stream()
                .anyMatch(teacher -> teacherIdText.equals(teacher.teacherId()));
    }

    private <T> T getForObject(String uriTemplate, Class<T> responseType, Object... uriVariables) {
        try {
            return restClient.get()
                    .uri(uriTemplate, uriVariables)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        int code = response.getStatusCode().value();
                        if (code == 403) {
                            throw new BusinessException(ContentErrorCode.COURSEWARE_ACCESS_DENIED,
                                    "Course service access denied");
                        }
                        throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                                "Course service returned " + code);
                    })
                    .body(responseType);
        } catch (BusinessException failure) {
            throw failure;
        } catch (RestClientException failure) {
            LOGGER.warn("Course service error on uri {}: {}", uriTemplate, failure.getMessage());
            throw new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                    "Course service unavailable", null, failure);
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

    private record EnrollmentStatus(
            String courseId,
            String studentId,
            String status,
            boolean enrolled) {
    }

    /** course 内部课程归属快照（雪花 ID 均字符串化）。 */
    private record CourseAccess(
            String courseId,
            String lifecycleStatus,
            String publishedVersionId,
            String draftVersionId,
            String ownerTeacherId,
            boolean contentReady,
            List<TeacherRef> teachers) {
    }

    private record TeacherRef(String teacherId, String teacherRole) {
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }
}
