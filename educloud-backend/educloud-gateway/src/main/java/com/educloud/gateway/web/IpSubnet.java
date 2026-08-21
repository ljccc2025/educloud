package com.educloud.gateway.web;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class IpSubnet {

    private final byte[] network;
    private final int prefixLength;

    private IpSubnet(byte[] address, int prefixLength) {
        this.network = address.clone();
        this.prefixLength = prefixLength;
        clearHostBits(this.network, prefixLength);
    }

    public static IpSubnet parse(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw invalidCidr();
        }
        String[] parts = value.split("/", -1);
        if (parts.length != 2 || !parts[1].matches("[0-9]{1,3}")) {
            throw invalidCidr();
        }

        ParsedAddress address = parseLiteral(parts[0]);
        int prefix;
        try {
            prefix = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ignored) {
            throw invalidCidr();
        }
        if (prefix < 0 || prefix > address.bytes().length * 8) {
            throw invalidCidr();
        }
        return new IpSubnet(address.bytes(), prefix);
    }

    public boolean contains(String address) {
        try {
            return contains(parseLiteral(address).bytes());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public boolean contains(InetAddress address) {
        Objects.requireNonNull(address, "address");
        return contains(address.getAddress());
    }

    private boolean contains(byte[] candidate) {
        if (candidate.length != network.length) {
            return false;
        }
        int fullBytes = prefixLength / 8;
        int partialBits = prefixLength % 8;
        for (int index = 0; index < fullBytes; index++) {
            if (network[index] != candidate[index]) {
                return false;
            }
        }
        if (partialBits == 0) {
            return true;
        }
        int mask = 0xff << (8 - partialBits);
        return (network[fullBytes] & mask) == (candidate[fullBytes] & mask);
    }

    static ParsedAddress parseLiteral(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw invalidAddress();
        }
        byte[] bytes = value.contains(":") ? parseIpv6(value) : parseIpv4(value);
        return new ParsedAddress(bytes, canonical(bytes));
    }

    static ParsedAddress fromInetAddress(InetAddress address) {
        Objects.requireNonNull(address, "address");
        byte[] bytes = address.getAddress();
        if (bytes.length != 4 && bytes.length != 16) {
            throw invalidAddress();
        }
        return new ParsedAddress(bytes, canonical(bytes));
    }

    private static byte[] parseIpv4(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            throw invalidAddress();
        }
        byte[] bytes = new byte[4];
        for (int index = 0; index < octets.length; index++) {
            String octet = octets[index];
            if (!octet.matches("0|[1-9][0-9]{0,2}")) {
                throw invalidAddress();
            }
            int parsed;
            try {
                parsed = Integer.parseInt(octet);
            } catch (NumberFormatException ignored) {
                throw invalidAddress();
            }
            if (parsed > 255) {
                throw invalidAddress();
            }
            bytes[index] = (byte) parsed;
        }
        return bytes;
    }

    private static byte[] parseIpv6(String original) {
        if (!original.matches("[0-9A-Fa-f:.]+")
                || original.indexOf("::") != original.lastIndexOf("::")) {
            throw invalidAddress();
        }

        String value = replaceEmbeddedIpv4(original);
        boolean compressed = value.contains("::");
        String[] halves = compressed ? value.split("::", -1) : new String[]{value};
        if (halves.length > 2) {
            throw invalidAddress();
        }
        List<Integer> left = parseIpv6Groups(halves[0]);
        List<Integer> right = halves.length == 2 ? parseIpv6Groups(halves[1]) : List.of();
        int explicitGroups = left.size() + right.size();
        if ((!compressed && explicitGroups != 8) || (compressed && explicitGroups >= 8)) {
            throw invalidAddress();
        }

        List<Integer> groups = new ArrayList<>(8);
        groups.addAll(left);
        if (compressed) {
            for (int count = explicitGroups; count < 8; count++) {
                groups.add(0);
            }
        }
        groups.addAll(right);
        if (groups.size() != 8) {
            throw invalidAddress();
        }

        byte[] bytes = new byte[16];
        for (int index = 0; index < groups.size(); index++) {
            int group = groups.get(index);
            bytes[index * 2] = (byte) (group >>> 8);
            bytes[index * 2 + 1] = (byte) group;
        }
        return bytes;
    }

    private static String replaceEmbeddedIpv4(String value) {
        if (!value.contains(".")) {
            return value;
        }
        int separator = value.lastIndexOf(':');
        if (separator < 0) {
            throw invalidAddress();
        }
        byte[] ipv4 = parseIpv4(value.substring(separator + 1));
        int high = (ipv4[0] & 0xff) << 8 | ipv4[1] & 0xff;
        int low = (ipv4[2] & 0xff) << 8 | ipv4[3] & 0xff;
        return value.substring(0, separator + 1)
                + Integer.toHexString(high) + ":" + Integer.toHexString(low);
    }

    private static List<Integer> parseIpv6Groups(String value) {
        if (value.isEmpty()) {
            return List.of();
        }
        String[] parts = value.split(":", -1);
        List<Integer> groups = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.matches("[0-9A-Fa-f]{1,4}")) {
                throw invalidAddress();
            }
            groups.add(Integer.parseInt(part, 16));
        }
        return groups;
    }

    private static String canonical(byte[] bytes) {
        if (bytes.length == 4) {
            return (bytes[0] & 0xff) + "." + (bytes[1] & 0xff) + "."
                    + (bytes[2] & 0xff) + "." + (bytes[3] & 0xff);
        }
        int[] groups = new int[8];
        for (int index = 0; index < groups.length; index++) {
            groups[index] = (bytes[index * 2] & 0xff) << 8 | bytes[index * 2 + 1] & 0xff;
        }
        int bestStart = -1;
        int bestLength = 0;
        for (int index = 0; index < groups.length; ) {
            if (groups[index] != 0) {
                index++;
                continue;
            }
            int end = index;
            while (end < groups.length && groups[end] == 0) {
                end++;
            }
            int length = end - index;
            if (length >= 2 && length > bestLength) {
                bestStart = index;
                bestLength = length;
            }
            index = end;
        }
        if (bestStart < 0) {
            return joinGroups(groups, 0, groups.length);
        }
        String left = joinGroups(groups, 0, bestStart);
        String right = joinGroups(groups, bestStart + bestLength, groups.length);
        return left + "::" + right;
    }

    private static String joinGroups(int[] groups, int start, int end) {
        StringBuilder value = new StringBuilder();
        for (int index = start; index < end; index++) {
            if (!value.isEmpty()) {
                value.append(':');
            }
            value.append(Integer.toHexString(groups[index]).toLowerCase(Locale.ROOT));
        }
        return value.toString();
    }

    private static void clearHostBits(byte[] address, int prefix) {
        int fullBytes = prefix / 8;
        int partialBits = prefix % 8;
        if (partialBits > 0) {
            int mask = 0xff << (8 - partialBits);
            address[fullBytes] = (byte) (address[fullBytes] & mask);
            fullBytes++;
        }
        Arrays.fill(address, fullBytes, address.length, (byte) 0);
    }

    private static IllegalArgumentException invalidCidr() {
        return new IllegalArgumentException("invalid numeric IP subnet");
    }

    private static IllegalArgumentException invalidAddress() {
        return new IllegalArgumentException("invalid numeric IP address");
    }

    static final class ParsedAddress {

        private final byte[] bytes;
        private final String canonical;

        private ParsedAddress(byte[] bytes, String canonical) {
            this.bytes = bytes.clone();
            this.canonical = canonical;
        }

        byte[] bytes() {
            return bytes.clone();
        }

        String canonical() {
            return canonical;
        }
    }
}
