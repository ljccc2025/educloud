package com.educloud.gateway.web;

import com.educloud.gateway.config.GatewayWebProperties;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public final class ClientIpResolver {

    private static final int MAX_PROXY_ELEMENTS = 16;
    private static final int MAX_PROXY_ELEMENT_LENGTH = 128;

    private final List<IpSubnet> trustedProxies;
    private final int trustedProxyHops;

    public ClientIpResolver(GatewayWebProperties properties) {
        Objects.requireNonNull(properties, "properties");
        this.trustedProxies = List.copyOf(properties.getTrustedProxyCidrs()).stream()
                .map(IpSubnet::parse)
                .toList();
        this.trustedProxyHops = properties.getTrustedProxyHops();
        if (trustedProxyHops < 1 || trustedProxyHops > MAX_PROXY_ELEMENTS) {
            throw new IllegalArgumentException("trustedProxyHops must be between 1 and 16");
        }
    }

    public String resolve(ServerHttpRequest request) {
        Objects.requireNonNull(request, "request");
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.isUnresolved() || remoteAddress.getAddress() == null) {
            throw new ClientIpResolutionException();
        }

        InetAddress peer = remoteAddress.getAddress();
        if (!isTrusted(peer)) {
            return IpSubnet.fromInetAddress(peer).canonical();
        }

        List<IpSubnet.ParsedAddress> chain;
        List<String> forwarded = request.getHeaders().get("Forwarded");
        if (forwarded != null) {
            chain = parseForwarded(forwarded);
        } else {
            List<String> xForwardedFor = request.getHeaders().get("X-Forwarded-For");
            chain = xForwardedFor == null ? List.of() : parseXForwardedFor(xForwardedFor);
        }
        if (chain.size() < trustedProxyHops) {
            throw new ClientIpResolutionException();
        }

        int clientIndex = chain.size() - trustedProxyHops;
        for (int index = clientIndex + 1; index < chain.size(); index++) {
            if (!isTrusted(chain.get(index).bytes())) {
                throw new ClientIpResolutionException();
            }
        }
        return chain.get(clientIndex).canonical();
    }

    private List<IpSubnet.ParsedAddress> parseForwarded(List<String> headerValues) {
        List<String> elements = new ArrayList<>();
        for (String headerValue : headerValues) {
            elements.addAll(splitForwarded(headerValue));
        }
        validateElementCount(elements);
        return elements.stream().map(this::parseForwardedElement).toList();
    }

    private List<IpSubnet.ParsedAddress> parseXForwardedFor(List<String> headerValues) {
        List<String> elements = new ArrayList<>();
        for (String headerValue : headerValues) {
            if (headerValue == null) {
                throw new ClientIpResolutionException();
            }
            for (String element : headerValue.split(",", -1)) {
                elements.add(element);
            }
        }
        validateElementCount(elements);
        List<IpSubnet.ParsedAddress> addresses = new ArrayList<>(elements.size());
        for (String element : elements) {
            String value = validateElement(element);
            if (isUnknownOrObfuscated(value) || value.contains("[") || value.contains("]")) {
                throw new ClientIpResolutionException();
            }
            try {
                addresses.add(IpSubnet.parseLiteral(value));
            } catch (IllegalArgumentException ignored) {
                throw new ClientIpResolutionException();
            }
        }
        return List.copyOf(addresses);
    }

    private IpSubnet.ParsedAddress parseForwardedElement(String element) {
        String value = validateElement(element);
        String forValue = null;
        for (String parameter : value.split(";", -1)) {
            String trimmed = parameter.trim();
            int separator = trimmed.indexOf('=');
            if (separator <= 0 || separator == trimmed.length() - 1) {
                throw new ClientIpResolutionException();
            }
            String name = trimmed.substring(0, separator).trim();
            String parameterValue = trimmed.substring(separator + 1).trim();
            if (!name.matches("[A-Za-z][A-Za-z0-9_-]*")) {
                throw new ClientIpResolutionException();
            }
            if ("for".equalsIgnoreCase(name)) {
                if (forValue != null) {
                    throw new ClientIpResolutionException();
                }
                forValue = unquote(parameterValue);
            }
        }
        if (forValue == null || isUnknownOrObfuscated(forValue)) {
            throw new ClientIpResolutionException();
        }
        return parseForwardedNode(forValue);
    }

    private IpSubnet.ParsedAddress parseForwardedNode(String value) {
        String address = value;
        if (value.startsWith("[")) {
            int closing = value.indexOf(']');
            if (closing <= 1 || value.indexOf(']', closing + 1) >= 0) {
                throw new ClientIpResolutionException();
            }
            address = value.substring(1, closing);
            if (!address.contains(":")) {
                throw new ClientIpResolutionException();
            }
            String suffix = value.substring(closing + 1);
            if (!suffix.isEmpty()) {
                if (!suffix.startsWith(":") || !isValidPort(suffix.substring(1))) {
                    throw new ClientIpResolutionException();
                }
            }
        } else if (value.contains("]") || value.contains("[")) {
            throw new ClientIpResolutionException();
        } else {
            int firstColon = value.indexOf(':');
            if (firstColon >= 0) {
                if (firstColon != value.lastIndexOf(':') || !value.substring(0, firstColon).contains(".")) {
                    throw new ClientIpResolutionException();
                }
                if (!isValidPort(value.substring(firstColon + 1))) {
                    throw new ClientIpResolutionException();
                }
                address = value.substring(0, firstColon);
            }
        }
        try {
            return IpSubnet.parseLiteral(address);
        } catch (IllegalArgumentException ignored) {
            throw new ClientIpResolutionException();
        }
    }

    private static List<String> splitForwarded(String headerValue) {
        if (headerValue == null) {
            throw new ClientIpResolutionException();
        }
        List<String> elements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < headerValue.length(); index++) {
            char character = headerValue.charAt(index);
            if (character == '\\') {
                throw new ClientIpResolutionException();
            }
            if (character == '"') {
                quoted = !quoted;
            }
            if (character == ',' && !quoted) {
                elements.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        if (quoted) {
            throw new ClientIpResolutionException();
        }
        elements.add(current.toString());
        return elements;
    }

    private static String unquote(String value) {
        boolean starts = value.startsWith("\"");
        boolean ends = value.endsWith("\"");
        if (starts != ends || value.contains("\\")) {
            throw new ClientIpResolutionException();
        }
        String unquoted = starts ? value.substring(1, value.length() - 1) : value;
        if (unquoted.isEmpty() || unquoted.contains("\"")) {
            throw new ClientIpResolutionException();
        }
        return unquoted;
    }

    private static void validateElementCount(List<String> elements) {
        if (elements.isEmpty() || elements.size() > MAX_PROXY_ELEMENTS) {
            throw new ClientIpResolutionException();
        }
    }

    private static String validateElement(String element) {
        if (element == null) {
            throw new ClientIpResolutionException();
        }
        String trimmed = element.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_PROXY_ELEMENT_LENGTH) {
            throw new ClientIpResolutionException();
        }
        return trimmed;
    }

    private boolean isTrusted(InetAddress address) {
        return trustedProxies.stream().anyMatch(subnet -> subnet.contains(address));
    }

    private boolean isTrusted(byte[] address) {
        try {
            return isTrusted(InetAddress.getByAddress(address));
        } catch (java.net.UnknownHostException impossible) {
            return false;
        }
    }

    private static boolean isUnknownOrObfuscated(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return "unknown".equals(normalized) || normalized.startsWith("_");
    }

    private static boolean isValidPort(String value) {
        if (!value.matches("[0-9]{1,5}")) {
            return false;
        }
        int port = Integer.parseInt(value);
        return port >= 1 && port <= 65535;
    }

    public static final class ClientIpResolutionException extends RuntimeException {

        public ClientIpResolutionException() {
            super("client address could not be resolved safely");
        }
    }
}
