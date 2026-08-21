package com.educloud.gateway.ratelimit;

import com.educloud.gateway.config.GatewayRateLimitProperties;
import com.educloud.gateway.error.GatewayErrorCode;
import com.educloud.gateway.error.GatewayErrorWriter;
import com.educloud.gateway.error.GatewayFailure;
import com.educloud.gateway.observability.GatewayMetrics;
import com.educloud.gateway.route.AccessDecision;
import com.educloud.gateway.route.AccessKind;
import com.educloud.gateway.route.AccessPolicy;
import com.educloud.gateway.web.GatewayExchangeAttributes;
import com.educloud.gateway.web.GatewayFilterOrders;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public final class GatewayRateLimitWebFilter implements WebFilter, Ordered {

    private final AccessPolicy accessPolicy;
    private final RedisTokenBucketLimiter limiter;
    private final HmacKeyHasher hasher;
    private final LoginNameExtractor loginNameExtractor;
    private final BucketRule ordinaryRule;
    private final BucketRule loginIpRule;
    private final BucketRule loginAccountRule;
    private final BucketRule paymentCallbackRule;
    private final GatewayErrorWriter errorWriter;
    private final GatewayMetrics metrics;

    @Autowired
    public GatewayRateLimitWebFilter(
            AccessPolicy accessPolicy,
            RedisTokenBucketLimiter limiter,
            HmacKeyHasher hasher,
            LoginNameExtractor loginNameExtractor,
            GatewayRateLimitProperties properties,
            GatewayErrorWriter errorWriter,
            ObjectProvider<GatewayMetrics> metricsProvider) {
        this(accessPolicy, limiter, hasher, loginNameExtractor, properties, errorWriter,
                metricsProvider.getIfAvailable(GatewayMetrics::noOp));
    }

    GatewayRateLimitWebFilter(
            AccessPolicy accessPolicy,
            RedisTokenBucketLimiter limiter,
            HmacKeyHasher hasher,
            LoginNameExtractor loginNameExtractor,
            GatewayRateLimitProperties properties,
            GatewayErrorWriter errorWriter,
            GatewayMetrics metrics) {
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.limiter = Objects.requireNonNull(limiter, "limiter");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.loginNameExtractor = Objects.requireNonNull(loginNameExtractor, "loginNameExtractor");
        Objects.requireNonNull(properties, "properties");
        this.ordinaryRule = rule(properties.getOrdinary());
        this.loginIpRule = rule(properties.getLoginIp());
        this.loginAccountRule = rule(properties.getLoginAccount());
        this.paymentCallbackRule = rule(properties.getPaymentCallback());
        this.errorWriter = Objects.requireNonNull(errorWriter, "errorWriter");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        AccessDecision access = accessPolicy.classify(
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().pathWithinApplication());
        if (access.kind() == AccessKind.ACTUATOR_HEALTH) {
            return chain.filter(exchange);
        }

        List<BucketRequest> buckets;
        try {
            buckets = buckets(exchange, access);
        } catch (UnsupportedLoginMediaType exception) {
            return errorWriter.write(exchange,
                    GatewayFailure.of(GatewayErrorCode.GATEWAY_UNSUPPORTED_MEDIA_TYPE));
        } catch (LoginNameExtractor.LoginNameExtractionException | IllegalStateException exception) {
            return errorWriter.write(exchange, GatewayFailure.of(GatewayErrorCode.GATEWAY_BAD_REQUEST));
        } finally {
            GatewayExchangeAttributes.clearCachedRequestBody(exchange);
        }

        Mono<LimitOutcome> outcome = Mono.defer(() -> limiter.acquire(buckets))
                .map(decision -> (LimitOutcome) new DecisionOutcome(decision))
                .onErrorReturn(DependencyFailure.INSTANCE);
        return outcome.flatMap(result -> {
            if (result == DependencyFailure.INSTANCE) {
                metrics.recordRateLimitDecision(
                        GatewayMetrics.RateLimitResult.DEPENDENCY_ERROR, access.routeGroup());
                if (access.kind() == AccessKind.PUBLIC_READ && !hasBearer(exchange)) {
                    metrics.recordRateLimitDegraded(access.routeGroup());
                    return chain.filter(exchange);
                }
                return errorWriter.write(
                        exchange, GatewayFailure.of(GatewayErrorCode.DEPENDENCY_UNAVAILABLE));
            }
            RateLimitDecision decision = ((DecisionOutcome) result).decision();
            if (decision.allowed()) {
                metrics.recordRateLimitDecision(
                        GatewayMetrics.RateLimitResult.ALLOWED, access.routeGroup());
                return chain.filter(exchange);
            }
            metrics.recordRateLimitDecision(
                    GatewayMetrics.RateLimitResult.DENIED, access.routeGroup());
            return errorWriter.write(exchange, GatewayFailure.rateLimited(decision.retryAfter()));
        });
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrders.RATE_LIMIT;
    }

    private List<BucketRequest> buckets(ServerWebExchange exchange, AccessDecision access) {
        Object clientIpValue = exchange.getAttributes().remove(GatewayExchangeAttributes.CLIENT_IP);
        if (!(clientIpValue instanceof String clientIp) || clientIp.isBlank()) {
            throw new IllegalStateException("client IP is not available");
        }

        List<BucketRequest> buckets = new ArrayList<>(3);
        String ordinaryDigest = hasher.digest("ordinary", access.routeGroup() + "\n" + clientIp);
        buckets.add(limiter.bucket("ordinary", ordinaryDigest, ordinaryRule));

        if (isLogin(exchange)) {
            MediaType contentType = exchange.getRequest().getHeaders().getContentType();
            if (contentType == null
                    || !"application".equalsIgnoreCase(contentType.getType())
                    || !"json".equalsIgnoreCase(contentType.getSubtype())) {
                throw new UnsupportedLoginMediaType();
            }
            byte[] body = GatewayExchangeAttributes.cachedRequestBody(exchange).orElse(new byte[0]);
            String loginName = loginNameExtractor.extract(body);
            buckets.add(limiter.bucket(
                    "login-ip", hasher.digest("login-ip", clientIp), loginIpRule));
            buckets.add(limiter.bucket(
                    "login-account", hasher.digest("login-account", loginName), loginAccountRule));
        } else if (access.kind() == AccessKind.PAYMENT_CALLBACK) {
            buckets.add(limiter.bucket(
                    "payment-callback",
                    hasher.digest("payment-callback", clientIp),
                    paymentCallbackRule));
        }
        return List.copyOf(buckets);
    }

    private static boolean isLogin(ServerWebExchange exchange) {
        return HttpMethod.POST.equals(exchange.getRequest().getMethod())
                && "/api/v1/auth/login".equals(
                        exchange.getRequest().getPath().pathWithinApplication().value());
    }

    private static boolean hasBearer(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.matches("(?i)^Bearer(?:\\s|$).*");
    }

    private static BucketRule rule(GatewayRateLimitProperties.Bucket bucket) {
        return new BucketRule(bucket.requests(), bucket.period(), bucket.burst());
    }

    private sealed interface LimitOutcome permits DecisionOutcome, DependencyFailure {
    }

    private record DecisionOutcome(RateLimitDecision decision) implements LimitOutcome {
    }

    private enum DependencyFailure implements LimitOutcome {
        INSTANCE
    }

    private static final class UnsupportedLoginMediaType extends RuntimeException {
    }
}
