package com.educloud.gateway.route;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRouteContractTest {

    private static Binder binder;
    private static List<RouteDefinition> routes;

    @BeforeAll
    static void loadRealApplicationConfiguration() throws IOException {
        List<PropertySource<?>> loaded = new YamlPropertySourceLoader().load(
                "gateway-routes", new ClassPathResource("application.yml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        loaded.forEach(propertySources::addLast);
        binder = new Binder(
                ConfigurationPropertySources.from(propertySources),
                null,
                ApplicationConversionService.getSharedInstance());
        routes = binder.bind("spring.cloud.gateway.routes", Bindable.listOf(RouteDefinition.class))
                .orElse(List.of());
    }

    @Test
    void declaresTheExactStaticRouteInventory() {
        assertThat(routes).hasSize(17);
        assertThat(routes).extracting(RouteDefinition::getId).containsExactly(
                "user-core", "user-me", "content-me", "course-enrollments",
                "content-course-scoped", "content-core", "content-drafts", "course-core",
                "order-core", "payment-core", "live-http", "live-ws", "file-core",
                "notification-core", "analytics-core", "search-core", "recommendation-core");
        assertThat(routes).extracting(RouteDefinition::getOrder).containsExactly(
                10, 20, 30, 40, 50, 60, 65, 70, 80, 90, 100, 101, 110, 120, 130, 140, 150);

        assertThat(targetServiceIds()).containsExactlyInAnyOrder(
                "educloud-user", "educloud-course", "educloud-content", "educloud-order",
                "educloud-payment", "educloud-live", "educloud-file",
                "educloud-notification", "educloud-analytics", "educloud-search",
                "educloud-recommendation");

        assertThat(binder.bind("spring.cloud.gateway.discovery.locator.enabled", Boolean.class)
                .orElseThrow(() -> new AssertionError("discovery locator setting is missing"))).isFalse();
        assertThat(allRoutePaths()).noneMatch(path -> path.contains("/internal/v1"));
        assertThat(allRoutePaths()).doesNotContain("/**");
        assertThat(routes).flatExtracting(RouteDefinition::getFilters)
                .extracting(FilterDefinition::getName)
                .noneMatch("Retry"::equalsIgnoreCase);
    }

    @Test
    void preservesOverlappingPathPrecedenceAndWebSocketScheme() {
        Map<String, Integer> indexById = new LinkedHashMap<>();
        for (int index = 0; index < routes.size(); index++) {
            indexById.put(routes.get(index).getId(), index);
        }
        assertThat(indexById.get("content-course-scoped"))
                .isLessThan(indexById.get("course-core"));
        assertThat(indexById.get("content-drafts"))
                .isLessThan(indexById.get("course-core"));

        RouteDefinition liveWebSocket = route("live-ws");
        assertThat(liveWebSocket.getUri().toString()).isEqualTo("lb:ws://educloud-live");
        assertThat(pathArguments(liveWebSocket)).containsExactly("/ws/v1/live/**");
    }

    @Test
    void locksEveryRoutePathPredicate() {
        assertThat(pathArguments(route("user-core"))).containsExactly(
                "/api/v1/auth/**", "/api/v1/users/**", "/api/v1/roles/**",
                "/api/v1/permissions/**", "/api/v1/platform-config/**", "/api/v1/security/**");
        assertThat(pathArguments(route("user-me"))).containsExactly(
                "/api/v1/me", "/api/v1/me/profile");
        assertThat(pathArguments(route("content-me"))).containsExactly(
                "/api/v1/me/assignments", "/api/v1/me/exams", "/api/v1/me/course-progress",
                "/api/v1/me/courses/*/progress");
        assertThat(pathArguments(route("course-enrollments"))).containsExactly("/api/v1/me/enrollments");
        assertThat(pathArguments(route("content-course-scoped"))).containsExactly(
                "/api/v1/courses/*/chapters/**", "/api/v1/courses/*/assignments/**",
                "/api/v1/courses/*/exams/**");
        assertThat(pathArguments(route("content-core"))).containsExactly(
                "/api/v1/chapters/**", "/api/v1/coursewares/**", "/api/v1/content-revisions/**",
                "/api/v1/assignments/**", "/api/v1/submissions/**", "/api/v1/exams/**",
                "/api/v1/exam-attempts/**", "/api/v1/community/**", "/api/v1/content-audits/**");
        assertThat(pathArguments(route("content-drafts"))).containsExactly(
                "/api/v1/teacher/courses/*/content-draft", "/api/v1/courses/*/content-drafts");
        assertThat(pathArguments(route("course-core"))).containsExactly(
                "/api/v1/categories/**", "/api/v1/course-drafts/**", "/api/v1/course-audits/**",
                "/api/v1/courses/**", "/api/v1/course-reviews/**",
                "/api/v1/teacher/courses/*/draft");
        assertThat(pathArguments(route("order-core"))).containsExactly(
                "/api/v1/cart/**", "/api/v1/orders/**", "/api/v1/refund-requests/**");
        assertThat(pathArguments(route("payment-core"))).containsExactly(
                "/api/v1/payments/**", "/api/v1/payment-callbacks/**",
                "/api/v1/payment-refunds/**", "/api/v1/reconciliations/**");
        assertThat(pathArguments(route("live-http"))).containsExactly("/api/v1/live-rooms/**");
        assertThat(pathArguments(route("file-core"))).containsExactly(
                "/api/v1/files/**", "/api/v1/file-upload-sessions/**");
        assertThat(pathArguments(route("notification-core"))).containsExactly(
                "/api/v1/notifications/**", "/api/v1/notification-channels/**");
        assertThat(pathArguments(route("analytics-core"))).containsExactly(
                "/api/v1/analytics/**", "/api/v1/audit-events/**");
        assertThat(pathArguments(route("search-core"))).containsExactly("/api/v1/search/**");
        assertThat(pathArguments(route("recommendation-core"))).containsExactly(
                "/api/v1/recommendations/**", "/api/v1/assistant/**");
    }

    @Test
    void routesCourseReviewsToCourseGroupAndCourseCore() {
        // P1a 规格审查：DELETE /api/v1/course-reviews/{id} 必须经网关 course-core
        // 路由可达（限流/分类与 COURSE 组一致），且 RouteGroups 归 COURSE 组。
        assertThat(RouteGroups.forPath(
                org.springframework.http.server.PathContainer.parsePath("/api/v1/course-reviews/501")))
                .isEqualTo(RouteGroups.COURSE);
        assertThat(pathArguments(route("course-core"))).contains("/api/v1/course-reviews/**");
    }

    private static RouteDefinition route(String id) {
        return routes.stream().filter(route -> id.equals(route.getId())).findFirst().orElseThrow();
    }

    private static List<String> pathArguments(RouteDefinition route) {
        return route.getPredicates().stream()
                .filter(predicate -> "Path".equals(predicate.getName()))
                .map(PredicateDefinition::getArgs)
                .flatMap(arguments -> arguments.values().stream())
                .toList();
    }

    private static List<String> allRoutePaths() {
        return routes.stream().flatMap(route -> pathArguments(route).stream()).toList();
    }

    private static Set<String> targetServiceIds() {
        return routes.stream()
                .map(route -> route.getUri().toString())
                .map(uri -> uri.startsWith("lb:ws://") ? uri.substring("lb:ws://".length())
                        : uri.substring("lb://".length()))
                .collect(Collectors.toSet());
    }
}
