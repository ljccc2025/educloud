package com.educloud.gateway.web;

import com.educloud.gateway.config.GatewayWebProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTest {

    @Test
    void ignoresAllForwardingHeadersFromAnUntrustedPeer() {
        ClientIpResolver resolver = resolver(List.of("10.0.0.0/8"), 1);
        MockServerHttpRequest request = request("203.0.113.9")
                .header("Forwarded", "for=unknown")
                .header("X-Forwarded-For", "not-an-address")
                .build();

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void selectsTheConfiguredHopFromTheRightOfForwarded() {
        ClientIpResolver resolver = resolver(List.of("10.0.0.0/8"), 2);
        MockServerHttpRequest request = request("10.0.0.2")
                .header("Forwarded", "for=198.51.100.25;proto=https, for=10.0.0.1")
                .build();

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.25");
    }

    @Test
    void parsesBracketedForwardedIpv6AndRemovesItsPort() {
        ClientIpResolver resolver = resolver(List.of("10.0.0.0/8"), 1);
        MockServerHttpRequest request = request("10.0.0.2")
                .header("Forwarded", "for=\"[2001:db8::1]:443\";proto=https")
                .build();

        assertThat(resolver.resolve(request)).isEqualTo("2001:db8::1");
    }

    @Test
    void usesXForwardedForWhenForwardedIsAbsent() {
        ClientIpResolver resolver = resolver(List.of("10.0.0.0/8"), 2);
        MockServerHttpRequest request = request("10.0.0.2")
                .header("X-Forwarded-For", "198.51.100.25, 10.0.0.1")
                .build();

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.25");
    }

    @Test
    void forwardedTakesPriorityOverXForwardedFor() {
        ClientIpResolver resolver = resolver(List.of("10.0.0.0/8"), 1);
        MockServerHttpRequest request = request("10.0.0.2")
                .header("Forwarded", "for=198.51.100.25")
                .header("X-Forwarded-For", "unknown")
                .build();

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.25");
    }

    @Test
    void rejectsUnsafeOrMalformedTrustedProxyChains() {
        ClientIpResolver resolver = resolver(List.of("10.0.0.0/8"), 1);
        for (String forwarded : List.of(
                "",
                "for=",
                "for=unknown",
                "for=_hidden",
                "for=host.example",
                "for=[2001:db8::1",
                "for=2001:db8::1",
                "for=[2001:db8::1]:70000",
                "for=192.0.2.1:not-a-port",
                "for=\"192.0.2.1\\evil\"",
                "proto=https")) {
            MockServerHttpRequest request = request("10.0.0.2")
                    .header("Forwarded", forwarded)
                    .build();
            assertThatThrownBy(() -> resolver.resolve(request))
                    .as(forwarded)
                    .isInstanceOf(ClientIpResolver.ClientIpResolutionException.class);
        }
    }

    @Test
    void rejectsEmptyInvalidAndBracketedXForwardedForElements() {
        ClientIpResolver resolver = resolver(List.of("10.0.0.0/8"), 1);
        for (String xff : List.of(
                "198.51.100.1,",
                "unknown",
                "_hidden",
                "[2001:db8::1]",
                "192.0.2.1:443",
                "host.example")) {
            MockServerHttpRequest request = request("10.0.0.2")
                    .header("X-Forwarded-For", xff)
                    .build();
            assertThatThrownBy(() -> resolver.resolve(request))
                    .as(xff)
                    .isInstanceOf(ClientIpResolver.ClientIpResolutionException.class);
        }
    }

    @Test
    void rejectsChainsThatAreTooShortTooLongOrContainOversizedElements() {
        ClientIpResolver twoHops = resolver(List.of("10.0.0.0/8"), 2);
        assertThatThrownBy(() -> twoHops.resolve(request("10.0.0.2")
                .header("X-Forwarded-For", "198.51.100.1").build()))
                .isInstanceOf(ClientIpResolver.ClientIpResolutionException.class);

        ClientIpResolver oneHop = resolver(List.of("10.0.0.0/8"), 1);
        String seventeen = String.join(",", java.util.Collections.nCopies(17, "198.51.100.1"));
        assertThatThrownBy(() -> oneHop.resolve(request("10.0.0.2")
                .header("X-Forwarded-For", seventeen).build()))
                .isInstanceOf(ClientIpResolver.ClientIpResolutionException.class);
        assertThatThrownBy(() -> oneHop.resolve(request("10.0.0.2")
                .header("Forwarded", "for=" + "1".repeat(129)).build()))
                .isInstanceOf(ClientIpResolver.ClientIpResolutionException.class);
    }

    @Test
    void rejectsAnUnresolvedOrMissingTcpPeer() {
        ClientIpResolver resolver = resolver(List.of(), 1);
        MockServerHttpRequest unresolved = MockServerHttpRequest.get("/")
                .remoteAddress(InetSocketAddress.createUnresolved("host.example", 443))
                .build();
        MockServerHttpRequest missing = MockServerHttpRequest.get("/").build();

        assertThatThrownBy(() -> resolver.resolve(unresolved))
                .isInstanceOf(ClientIpResolver.ClientIpResolutionException.class);
        assertThatThrownBy(() -> resolver.resolve(missing))
                .isInstanceOf(ClientIpResolver.ClientIpResolutionException.class);
    }

    private static ClientIpResolver resolver(List<String> cidrs, int hops) {
        GatewayWebProperties properties = new GatewayWebProperties();
        properties.setTrustedProxyCidrs(cidrs);
        properties.setTrustedProxyHops(hops);
        return new ClientIpResolver(properties);
    }

    private static MockServerHttpRequest.BaseBuilder<?> request(String peer) {
        return MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress(peer, 54321));
    }
}
