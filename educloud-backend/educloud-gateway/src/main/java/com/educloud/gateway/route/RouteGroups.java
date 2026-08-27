package com.educloud.gateway.route;

import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

public final class RouteGroups {

    public static final String AUTH = "auth";
    public static final String CATALOG = "catalog";
    public static final String PAYMENT_CALLBACK = "payment-callback";
    public static final String LIVE_WS = "live-ws";
    public static final String USER = "user";
    public static final String COURSE = "course";
    public static final String CONTENT = "content";
    public static final String ORDER = "order";
    public static final String PAYMENT = "payment";
    public static final String LIVE = "live";
    public static final String FILE = "file";
    public static final String NOTIFICATION = "notification";
    public static final String ANALYTICS = "analytics";
    public static final String SEARCH = "search";
    public static final String RECOMMENDATION = "recommendation";
    public static final String UNMATCHED = "unmatched";

    private static final PathPatternParser PARSER = new PathPatternParser();
    private static final List<RouteGroupRule> RULES = List.of(
            rule(LIVE_WS, "/ws/v1/live/**"),
            rule(AUTH, "/api/v1/auth/**"),
            rule(CONTENT,
                    "/api/v1/me/assignments", "/api/v1/me/exams", "/api/v1/me/course-progress",
                    "/api/v1/me/courses/*/progress", "/api/v1/courses/*/chapters", "/api/v1/courses/*/chapters/**",
                    "/api/v1/courses/*/assignments", "/api/v1/courses/*/assignments/**",
                    "/api/v1/courses/*/exams", "/api/v1/courses/*/exams/**",
                    "/api/v1/chapters", "/api/v1/chapters/**",
                    "/api/v1/coursewares", "/api/v1/coursewares/**",
                    "/api/v1/content-revisions", "/api/v1/content-revisions/**",
                    "/api/v1/assignments", "/api/v1/assignments/**",
                    "/api/v1/submissions", "/api/v1/submissions/**",
                    "/api/v1/exams", "/api/v1/exams/**",
                    "/api/v1/exam-attempts", "/api/v1/exam-attempts/**",
                    "/api/v1/community", "/api/v1/community/**",
                    "/api/v1/content-audits", "/api/v1/content-audits/**",
                    "/api/v1/teacher/courses/*/content-draft", "/api/v1/courses/*/content-drafts"),
            rule(COURSE,
                    "/api/v1/me/enrollments", "/api/v1/categories/**", "/api/v1/course-drafts/**",
                    "/api/v1/course-audits/**", "/api/v1/courses/**", "/api/v1/course-reviews/**",
                    "/api/v1/admin/courses", "/api/v1/teacher/courses",
                    "/api/v1/teacher/courses/*/draft"),
            rule(USER,
                    "/api/v1/users/**", "/api/v1/roles/**", "/api/v1/permissions/**",
                    "/api/v1/platform-config/**", "/api/v1/security/**",
                    "/api/v1/me", "/api/v1/me/profile"),
            rule(ORDER,
                    "/api/v1/cart/**", "/api/v1/orders/**", "/api/v1/refund-requests/**",
                    "/api/v1/admin/orders/**", "/api/v1/admin/refund-requests/**"),
            rule(PAYMENT,
                    "/api/v1/payments/**", "/api/v1/payment-callbacks/**",
                    "/api/v1/payment-refunds/**", "/api/v1/reconciliations/**"),
            rule(LIVE, "/api/v1/live-rooms/**"),
            rule(FILE, "/api/v1/files/**", "/api/v1/file-upload-sessions/**"),
            rule(NOTIFICATION, "/api/v1/notifications/**", "/api/v1/notification-channels/**"),
            rule(ANALYTICS, "/api/v1/analytics/**", "/api/v1/audit-events/**"),
            rule(SEARCH, "/api/v1/search/**"),
            rule(RECOMMENDATION, "/api/v1/recommendations/**", "/api/v1/assistant/**"));

    private RouteGroups() {
    }

    public static String forPath(PathContainer path) {
        return RULES.stream()
                .filter(rule -> rule.patterns().stream().anyMatch(pattern -> pattern.matches(path)))
                .map(RouteGroupRule::group)
                .findFirst()
                .orElse(UNMATCHED);
    }

    private static RouteGroupRule rule(String group, String... patterns) {
        return new RouteGroupRule(group, List.of(patterns).stream().map(PARSER::parse).toList());
    }

    private record RouteGroupRule(String group, List<PathPattern> patterns) {
    }
}
