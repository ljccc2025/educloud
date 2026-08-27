package com.educloud.gateway.security;

import com.educloud.gateway.error.GatewayErrorCode;
import com.educloud.gateway.error.GatewayErrorWriter;
import com.educloud.gateway.error.GatewayFailure;
import com.educloud.gateway.web.ClientIpResolver;
import com.educloud.gateway.web.GatewayExchangeAttributes;
import com.educloud.gateway.web.GatewayFilterOrders;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Component
public final class IdentityHeaderWebFilter implements WebFilter, Ordered {

    private static final Set<String> IDENTITY_HEADERS = Set.of(
            "x-user-id",
            "x-user-type",
            "x-role",
            "x-roles",
            "x-permission",
            "x-permissions",
            "x-authenticated-user");

    /** 网关注入的可信真实客户端 IP 头（客户端传入的同名头会被先剥离再覆盖写入） */
    private static final String X_REAL_IP_HEADER = "X-Real-IP";

    private final ClientIpResolver clientIpResolver;
    private final GatewayErrorWriter errorWriter;

    public IdentityHeaderWebFilter(
            ClientIpResolver clientIpResolver, GatewayErrorWriter errorWriter) {
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver, "clientIpResolver");
        this.errorWriter = Objects.requireNonNull(errorWriter, "errorWriter");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String clientIp;
        try {
            clientIp = clientIpResolver.resolve(exchange.getRequest());
        } catch (ClientIpResolver.ClientIpResolutionException exception) {
            return errorWriter.write(exchange, GatewayFailure.of(GatewayErrorCode.GATEWAY_BAD_REQUEST));
        }
        exchange.getAttributes().put(GatewayExchangeAttributes.CLIENT_IP, clientIp);

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    removeUntrustedHeaders(headers);
                    // 注入网关解析出的可信真实客户端 IP（BUG-040/071/077）：覆盖任何客户端传入值，
                    // 本部署网关是唯一入口，下游只能看到网关写入的 X-Real-IP。
                    headers.set(X_REAL_IP_HEADER, clientIp);
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrders.CLIENT_IDENTITY;
    }

    private static void removeUntrustedHeaders(HttpHeaders headers) {
        for (String name : new ArrayList<>(headers.keySet())) {
            String lower = name.toLowerCase(Locale.ROOT);
            if ("forwarded".equals(lower)
                    || lower.startsWith("x-forwarded-")
                    || "x-real-ip".equals(lower)
                    || IDENTITY_HEADERS.contains(lower)
                    || lower.startsWith("x-educloud-identity-")) {
                headers.remove(name);
            }
        }
    }
}
