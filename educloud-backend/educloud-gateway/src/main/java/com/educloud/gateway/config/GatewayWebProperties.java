package com.educloud.gateway.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;

@ConfigurationProperties("educloud.gateway.web")
@Validated
public final class GatewayWebProperties {

    private static final long MAX_GLOBAL_BODY_BYTES = DataSize.ofMegabytes(10).toBytes();
    private static final long MAX_AUTH_BODY_BYTES = DataSize.ofMegabytes(1).toBytes();
    private static final long MAX_PAYMENT_BODY_BYTES = DataSize.ofMegabytes(5).toBytes();
    private static final long MAX_HEADER_BYTES = DataSize.ofKilobytes(64).toBytes();
    private static final long MAX_INITIAL_LINE_BYTES = DataSize.ofKilobytes(16).toBytes();

    @NotEmpty
    private List<String> allowedOrigins = List.of();

    @NotNull
    private List<String> trustedProxyCidrs = List.of();

    @Min(value = 1, message = "trustedProxyHops must be at least 1")
    @Max(value = 16, message = "trustedProxyHops must not exceed 16")
    private int trustedProxyHops = 1;

    @NotNull
    private DataSize globalBodyLimit = DataSize.ofMegabytes(1);

    @NotNull
    private DataSize authBodyLimit = DataSize.ofKilobytes(16);

    @NotNull
    private DataSize paymentCallbackBodyLimit = DataSize.ofKilobytes(256);

    @NotNull
    private DataSize headerLimit = DataSize.ofKilobytes(16);

    @NotNull
    private DataSize initialLineLimit = DataSize.ofKilobytes(8);

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(2);

    @NotNull
    private Duration responseTimeout = Duration.ofSeconds(15);

    @AssertTrue(message = "allowedOrigins must be unique exact origins without wildcard, userinfo, path, query, or fragment")
    public boolean isAllowedOriginsValid() {
        return allowedOrigins != null
                && !allowedOrigins.isEmpty()
                && new HashSet<>(allowedOrigins).size() == allowedOrigins.size()
                && allowedOrigins.stream().allMatch(GatewayWebProperties::isExactHttpOrigin);
    }

    @AssertTrue(message = "trustedProxyCidrs must contain valid IPv4 or IPv6 CIDR values")
    public boolean isTrustedProxyCidrsValid() {
        return trustedProxyCidrs != null && trustedProxyCidrs.stream().allMatch(GatewayWebProperties::isValidCidr);
    }

    @AssertTrue(message = "request size and timeout limits must be positive and within the configured safety bounds")
    public boolean isResourceBoundsValid() {
        if (globalBodyLimit == null || authBodyLimit == null || paymentCallbackBodyLimit == null
                || headerLimit == null || initialLineLimit == null
                || connectTimeout == null || responseTimeout == null) {
            return false;
        }
        long global = globalBodyLimit.toBytes();
        long auth = authBodyLimit.toBytes();
        long payment = paymentCallbackBodyLimit.toBytes();
        long header = headerLimit.toBytes();
        long initialLine = initialLineLimit.toBytes();
        return isPositiveAtMost(global, MAX_GLOBAL_BODY_BYTES)
                && isPositiveAtMost(auth, MAX_AUTH_BODY_BYTES)
                && isPositiveAtMost(payment, MAX_PAYMENT_BODY_BYTES)
                && auth <= global
                && payment <= global
                && isPositiveAtMost(header, MAX_HEADER_BYTES)
                && isPositiveAtMost(initialLine, MAX_INITIAL_LINE_BYTES)
                && isPositiveAtMost(connectTimeout, Duration.ofSeconds(30))
                && isPositiveAtMost(responseTimeout, Duration.ofMinutes(2));
    }

    private static boolean isExactHttpOrigin(String value) {
        if (value == null || value.isBlank() || value.contains("*")) {
            return false;
        }
        try {
            URI origin = new URI(value);
            String scheme = origin.getScheme();
            return scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && origin.getHost() != null
                    && origin.getRawUserInfo() == null
                    && (origin.getRawPath() == null || origin.getRawPath().isEmpty())
                    && origin.getRawQuery() == null
                    && origin.getRawFragment() == null
                    && origin.getPort() != 0;
        } catch (URISyntaxException ignored) {
            return false;
        }
    }

    private static boolean isValidCidr(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String[] parts = value.split("/", -1);
        if (parts.length != 2 || !parts[1].matches("[0-9]{1,3}")) {
            return false;
        }
        boolean ipv6 = parts[0].contains(":");
        if (!(ipv6 ? parts[0].matches("[0-9A-Fa-f:.]+") : parts[0].matches("[0-9.]+"))) {
            return false;
        }
        if (!ipv6 && !isValidIpv4Literal(parts[0])) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(parts[0]);
            int prefix = Integer.parseInt(parts[1]);
            int expectedBytes = ipv6 ? 16 : 4;
            return address.getAddress().length == expectedBytes && prefix >= 0 && prefix <= expectedBytes * 8;
        } catch (UnknownHostException | NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isValidIpv4Literal(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            try {
                if (octet.isEmpty() || Integer.parseInt(octet) > 255) {
                    return false;
                }
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPositiveAtMost(long value, long maximum) {
        return value > 0 && value <= maximum;
    }

    private static boolean isPositiveAtMost(Duration value, Duration maximum) {
        return !value.isZero() && !value.isNegative() && value.compareTo(maximum) <= 0;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public List<String> getTrustedProxyCidrs() {
        return trustedProxyCidrs;
    }

    public void setTrustedProxyCidrs(List<String> trustedProxyCidrs) {
        this.trustedProxyCidrs = trustedProxyCidrs;
    }

    public int getTrustedProxyHops() {
        return trustedProxyHops;
    }

    public void setTrustedProxyHops(int trustedProxyHops) {
        this.trustedProxyHops = trustedProxyHops;
    }

    public DataSize getGlobalBodyLimit() {
        return globalBodyLimit;
    }

    public void setGlobalBodyLimit(DataSize globalBodyLimit) {
        this.globalBodyLimit = globalBodyLimit;
    }

    public DataSize getAuthBodyLimit() {
        return authBodyLimit;
    }

    public void setAuthBodyLimit(DataSize authBodyLimit) {
        this.authBodyLimit = authBodyLimit;
    }

    public DataSize getPaymentCallbackBodyLimit() {
        return paymentCallbackBodyLimit;
    }

    public void setPaymentCallbackBodyLimit(DataSize paymentCallbackBodyLimit) {
        this.paymentCallbackBodyLimit = paymentCallbackBodyLimit;
    }

    public DataSize getHeaderLimit() {
        return headerLimit;
    }

    public void setHeaderLimit(DataSize headerLimit) {
        this.headerLimit = headerLimit;
    }

    public DataSize getInitialLineLimit() {
        return initialLineLimit;
    }

    public void setInitialLineLimit(DataSize initialLineLimit) {
        this.initialLineLimit = initialLineLimit;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = responseTimeout;
    }
}
