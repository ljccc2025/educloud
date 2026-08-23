package com.educloud.user.controller;

import com.educloud.common.error.BusinessException;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.service.ServiceTokenService;
import com.educloud.user.support.RequestIds;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 内部服务令牌签发端点（POST /internal/v1/service-tokens）。
 *
 * <p>依据：M03 设计规格第 8 节 —— HTTP Basic（client_id:client_secret）+
 * 表单/JSON {@code grant_type=client_credentials&audience=<target>&scope=<...>}；
 * 成功响应只含 {@code access_token/token_type=Bearer/expires_in}。凭据校验复用
 * {@link ServiceTokenService}（secret 哈希、client ACTIVE、audience/scope 白名单）；
 * 凭据无效一律 401 且不暴露业务错误码细节。该端点使用 HTTP Basic 而非服务令牌，
 * 由 {@code InternalApiFilter.shouldNotFilter} 放行。</p>
 */
@RestController
@RequestMapping("/internal/v1")
public final class InternalServiceTokenController {

    private static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    private static final String TOKEN_TYPE_BEARER = "Bearer";

    private final ServiceTokenService serviceTokenService;

    public InternalServiceTokenController(ServiceTokenService serviceTokenService) {
        this.serviceTokenService = serviceTokenService;
    }

    @PostMapping(value = "/service-tokens",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> issueFromForm(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam("grant_type") String grantType,
            @RequestParam("audience") String audience,
            @RequestParam(value = "scope", required = false) String scope,
            HttpServletRequest servletRequest) {
        return issue(authorization, grantType, audience, scope, servletRequest);
    }

    @PostMapping(value = "/service-tokens",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> issueFromJson(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody TokenRequest request,
            HttpServletRequest servletRequest) {
        return issue(authorization, request.grantType(), request.audience(), request.scope(), servletRequest);
    }

    private ResponseEntity<Map<String, Object>> issue(
            String authorization, String grantType, String audience, String scope,
            HttpServletRequest servletRequest) {
        if (!GRANT_TYPE_CLIENT_CREDENTIALS.equals(grantType)) {
            // 不支持 grant_type：400，空响应体，不暴露实现细节。
            return ResponseEntity.badRequest().build();
        }
        String[] credentials = parseBasic(authorization);
        if (credentials == null) {
            return unauthorized();
        }
        try {
            ServiceTokenService.IssueResult result = serviceTokenService.issue(
                    credentials[0],
                    credentials[1],
                    audience,
                    parseScopes(scope),
                    servletRequest.getRemoteAddr(),
                    RequestIds.from(servletRequest));
            return ResponseEntity.ok(Map.of(
                    "access_token", result.accessToken(),
                    "token_type", TOKEN_TYPE_BEARER,
                    "expires_in", result.expiresIn()));
        } catch (BusinessException exception) {
            if (exception.errorCode() == UserErrorCode.SERVICE_CLIENT_DISABLED
                    || exception.errorCode() == UserErrorCode.SERVICE_TOKEN_SCOPE_DENIED) {
                // 非凭据问题：client 被禁用或 audience/scope 不在白名单，按规格以 403 拒绝，
                // 同样不携带业务错误码细节。
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            // 未知 client / secret 哈希不匹配 / 凭据失效 → 401，不暴露业务错误码。
            return unauthorized();
        }
    }

    private static List<String> parseScopes(String scope) {
        if (scope == null || scope.isBlank()) {
            return List.of();
        }
        return Arrays.stream(scope.trim().split("\\s+"))
                .filter(token -> !token.isEmpty())
                .toList();
    }

    private static String[] parseBasic(String authorization) {
        if (authorization == null
                || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        String encoded = authorization.substring(6).trim();
        if (encoded.isEmpty()) {
            return null;
        }
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        String raw = new String(decoded, StandardCharsets.UTF_8);
        int colon = raw.indexOf(':');
        if (colon <= 0 || colon == raw.length() - 1) {
            return null;
        }
        return new String[]{raw.substring(0, colon), raw.substring(colon + 1)};
    }

    private static ResponseEntity<Map<String, Object>> unauthorized() {
        // 空响应体：凭据无效不携带任何业务错误码细节。
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    /** JSON 请求体（与表单参数同构：grant_type/audience/scope）。 */
    public record TokenRequest(
            @JsonProperty("grant_type") String grantType,
            String audience,
            String scope) {
    }
}
