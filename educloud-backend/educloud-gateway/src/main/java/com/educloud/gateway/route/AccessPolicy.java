package com.educloud.gateway.route;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;
import java.util.Locale;

public interface AccessPolicy {

    AccessDecision classify(HttpMethod method, PathContainer path);

    static AccessPolicy standard() {
        return new PathPatternAccessPolicy();
    }
}

@Component
final class PathPatternAccessPolicy implements AccessPolicy {

    private static final PathPatternParser PARSER = new PathPatternParser();
    private static final PathPattern INTERNAL = pattern("/internal/v1/**");
    private static final List<PathPattern> ACTUATOR_HEALTH = patterns(
            "/actuator/health/liveness", "/actuator/health/readiness");
    private static final List<PathPattern> PUBLIC_READ = patterns(
            "/api/v1/platform-config/public",
            "/api/v1/categories",
            "/api/v1/courses",
            "/api/v1/courses/{courseId}",
            "/api/v1/courses/{courseId}/chapters",
            "/api/v1/coursewares/{coursewareId}/download-url",
            "/api/v1/search/courses",
            "/api/v1/recommendations/courses",
            "/ws/v1/live/**");
    // M03: logout 与 register/login/refresh 同为匿名可达（User 服务内做会话撤销）。
    private static final List<PathPattern> AUTH_SENSITIVE = patterns(
            "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
            "/api/v1/auth/logout");
    private static final PathPattern PAYMENT_CALLBACK = pattern(
            "/api/v1/payment-callbacks/{provider}/**");

    @Override
    public AccessDecision classify(HttpMethod method, PathContainer path) {
        if (INTERNAL.matches(path)) {
            return decision(AccessKind.INTERNAL, RouteGroups.UNMATCHED);
        }
        if (isRead(method) && matchesAny(ACTUATOR_HEALTH, path)) {
            return decision(AccessKind.ACTUATOR_HEALTH, RouteGroups.UNMATCHED);
        }
        if (!hasAmbiguousPathSyntax(path)) {
            if (isRead(method) && matchesAny(PUBLIC_READ, path)) {
                return decision(AccessKind.PUBLIC_READ, RouteGroups.CATALOG);
            }
            if (HttpMethod.POST.equals(method) && matchesAny(AUTH_SENSITIVE, path)) {
                return decision(AccessKind.AUTH_SENSITIVE, RouteGroups.AUTH);
            }
            if (HttpMethod.POST.equals(method) && PAYMENT_CALLBACK.matches(path)) {
                return decision(AccessKind.PAYMENT_CALLBACK, RouteGroups.PAYMENT_CALLBACK);
            }
        }
        return decision(AccessKind.PROTECTED, RouteGroups.forPath(path));
    }

    private static AccessDecision decision(AccessKind kind, String routeGroup) {
        return new AccessDecision(kind, routeGroup);
    }

    private static boolean isRead(HttpMethod method) {
        return HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method);
    }

    private static boolean matchesAny(List<PathPattern> patterns, PathContainer path) {
        return patterns.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private static boolean hasAmbiguousPathSyntax(PathContainer path) {
        String rawPath = path.value();
        String lowerPath = rawPath.toLowerCase(Locale.ROOT);
        return rawPath.contains("//")
                || rawPath.contains("\\")
                || rawPath.contains(";")
                || lowerPath.contains("%2f")
                || lowerPath.contains("%5c");
    }

    private static List<PathPattern> patterns(String... values) {
        return List.of(values).stream().map(PathPatternAccessPolicy::pattern).toList();
    }

    private static PathPattern pattern(String value) {
        return PARSER.parse(value);
    }
}
