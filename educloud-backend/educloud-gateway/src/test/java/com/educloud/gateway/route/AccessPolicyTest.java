package com.educloud.gateway.route;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AccessPolicyTest {

    private final AccessPolicy policy = AccessPolicy.standard();

    @ParameterizedTest
    @MethodSource("explicitAccessCases")
    void classifiesOnlyTheExplicitAccessMatrix(
            HttpMethod method, String path, AccessKind expectedKind, String expectedRouteGroup) {
        AccessDecision decision = policy.classify(method, PathContainer.parsePath(path));

        assertThat(decision.kind()).isEqualTo(expectedKind);
        assertThat(decision.routeGroup()).isEqualTo(expectedRouteGroup);
    }

    @ParameterizedTest
    @MethodSource("nearMissCases")
    void keepsNearMissesProtected(HttpMethod method, String path) {
        AccessDecision decision = policy.classify(method, PathContainer.parsePath(path));

        assertThat(decision.kind()).isEqualTo(AccessKind.PROTECTED);
        assertThat(decision.mayProceedWithoutBearer()).isFalse();
    }

    @Test
    void onlyExplicitExternalKindsMayProceedWithoutBearer() {
        assertThat(new AccessDecision(AccessKind.PUBLIC_READ, RouteGroups.CATALOG)
                .mayProceedWithoutBearer()).isTrue();
        assertThat(new AccessDecision(AccessKind.AUTH_SENSITIVE, RouteGroups.AUTH)
                .mayProceedWithoutBearer()).isTrue();
        assertThat(new AccessDecision(AccessKind.PAYMENT_CALLBACK, RouteGroups.PAYMENT_CALLBACK)
                .mayProceedWithoutBearer()).isTrue();
        assertThat(new AccessDecision(AccessKind.ACTUATOR_HEALTH, RouteGroups.UNMATCHED)
                .mayProceedWithoutBearer()).isTrue();
        assertThat(new AccessDecision(AccessKind.INTERNAL, RouteGroups.UNMATCHED)
                .mayProceedWithoutBearer()).isFalse();
        assertThat(new AccessDecision(AccessKind.PROTECTED, RouteGroups.USER)
                .mayProceedWithoutBearer()).isFalse();
    }

    private static Stream<Arguments> explicitAccessCases() {
        return Stream.of(
                publicRead(HttpMethod.GET, "/api/v1/platform-config/public"),
                publicRead(HttpMethod.HEAD, "/api/v1/platform-config/public"),
                publicRead(HttpMethod.GET, "/api/v1/categories"),
                publicRead(HttpMethod.HEAD, "/api/v1/categories"),
                publicRead(HttpMethod.GET, "/api/v1/courses"),
                publicRead(HttpMethod.HEAD, "/api/v1/courses/course-1"),
                publicRead(HttpMethod.GET, "/api/v1/search/courses"),
                publicRead(HttpMethod.GET, "/api/v1/search/suggest"),
                publicRead(HttpMethod.GET, "/api/v1/recommendations"),
                Arguments.of(HttpMethod.POST, "/api/v1/recommendations/feedback",
                        AccessKind.PROTECTED, RouteGroups.RECOMMENDATION),
                Arguments.of(HttpMethod.POST, "/api/v1/auth/register",
                        AccessKind.AUTH_SENSITIVE, RouteGroups.AUTH),
                Arguments.of(HttpMethod.POST, "/api/v1/auth/login",
                        AccessKind.AUTH_SENSITIVE, RouteGroups.AUTH),
                Arguments.of(HttpMethod.POST, "/api/v1/auth/refresh",
                        AccessKind.AUTH_SENSITIVE, RouteGroups.AUTH),
                Arguments.of(HttpMethod.POST, "/api/v1/payment-callbacks/provider/notify",
                        AccessKind.PAYMENT_CALLBACK, RouteGroups.PAYMENT_CALLBACK),
                Arguments.of(HttpMethod.DELETE, "/internal/v1/jobs/1",
                        AccessKind.INTERNAL, RouteGroups.UNMATCHED),
                Arguments.of(HttpMethod.GET, "/actuator/health/liveness",
                        AccessKind.ACTUATOR_HEALTH, RouteGroups.UNMATCHED),
                Arguments.of(HttpMethod.HEAD, "/actuator/health/readiness",
                        AccessKind.ACTUATOR_HEALTH, RouteGroups.UNMATCHED),
                Arguments.of(HttpMethod.GET, "/api/v1/users/me",
                        AccessKind.PROTECTED, RouteGroups.USER),
                Arguments.of(HttpMethod.POST, "/api/v1/courses",
                        AccessKind.PROTECTED, RouteGroups.COURSE),
                Arguments.of(HttpMethod.GET, "/api/v1/coursewares/1",
                        AccessKind.PROTECTED, RouteGroups.CONTENT),
                Arguments.of(HttpMethod.GET, "/api/v1/orders",
                        AccessKind.PROTECTED, RouteGroups.ORDER),
                Arguments.of(HttpMethod.POST, "/api/v1/orders",
                        AccessKind.PROTECTED, RouteGroups.ORDER),
                Arguments.of(HttpMethod.GET, "/api/v1/admin/orders",
                        AccessKind.PROTECTED, RouteGroups.ORDER),
                Arguments.of(HttpMethod.GET, "/api/v1/cart",
                        AccessKind.PROTECTED, RouteGroups.ORDER),
                publicRead(HttpMethod.GET, "/ws/v1/live/room-1"),
                Arguments.of(HttpMethod.GET, "/api/v1/not-routed",
                        AccessKind.PROTECTED, RouteGroups.UNMATCHED));
    }

    private static Stream<Arguments> nearMissCases() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/v1/courses"),
                Arguments.of(HttpMethod.GET, "/api/v1/courses/a/b"),
                Arguments.of(HttpMethod.GET, "/api/v1/courses/a/"),
                Arguments.of(HttpMethod.GET, "/api/v1/Courses"),
                Arguments.of(HttpMethod.GET, "/api/v1//courses"),
                Arguments.of(HttpMethod.GET, "/api/v1/courses/a%2Fb"),
                Arguments.of(HttpMethod.GET, "/api/v1/courses;view=public"),
                Arguments.of(HttpMethod.PUT, "/api/v1/auth/login"),
                Arguments.of(HttpMethod.GET, "/api/v1/auth/register"),
                Arguments.of(HttpMethod.GET, "/api/v1/payment-callbacks/provider"),
                Arguments.of(HttpMethod.POST, "/api/v1/payment-callbacks"),
                Arguments.of(HttpMethod.POST, "/actuator/health/liveness"));
    }

    private static Arguments publicRead(HttpMethod method, String path) {
        return Arguments.of(method, path, AccessKind.PUBLIC_READ, RouteGroups.CATALOG);
    }
}
