package com.educloud.gateway.web;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IpSubnetTest {

    @Test
    void matchesIpv4NetworkAndBroadcastBoundaries() throws Exception {
        IpSubnet subnet = IpSubnet.parse("10.0.0.0/8");

        assertThat(subnet.contains("10.0.0.0")).isTrue();
        assertThat(subnet.contains("10.255.255.255")).isTrue();
        assertThat(subnet.contains(InetAddress.getByAddress(new byte[]{10, 1, 2, 3}))).isTrue();
        assertThat(subnet.contains("11.0.0.0")).isFalse();
    }

    @Test
    void matchesIpv6AndZeroPrefixBoundaries() {
        IpSubnet documentation = IpSubnet.parse("2001:db8::/32");
        IpSubnet allIpv6 = IpSubnet.parse("::/0");

        assertThat(documentation.contains("2001:db8::")).isTrue();
        assertThat(documentation.contains("2001:db8:ffff:ffff:ffff:ffff:ffff:ffff")).isTrue();
        assertThat(documentation.contains("2001:db9::1")).isFalse();
        assertThat(allIpv6.contains("fe80::1")).isTrue();
        assertThat(allIpv6.contains("192.0.2.1")).isFalse();
    }

    @Test
    void keepsIpv4MappedIpv6InTheIpv6Family() {
        IpSubnet mapped = IpSubnet.parse("::ffff:192.0.2.0/120");
        IpSubnet ipv4 = IpSubnet.parse("192.0.2.0/24");

        assertThat(mapped.contains("::ffff:192.0.2.1")).isTrue();
        assertThat(mapped.contains("192.0.2.1")).isFalse();
        assertThat(ipv4.contains("::ffff:192.0.2.1")).isFalse();
    }

    @Test
    void rejectsHostnamesShorthandZonesAndInvalidPrefixes() {
        for (String value : new String[]{
                "localhost/32",
                "example.com/24",
                "10.0.0/8",
                "10.0.0.1/-1",
                "10.0.0.1/33",
                "2001:db8::1/129",
                "fe80::1%eth0/64",
                "2001:db8::gg/64",
                "10.0.0.256/24",
                "10.0.0.1",
                "10.0.0.1/24/extra"}) {
            assertThatThrownBy(() -> IpSubnet.parse(value))
                    .as(value)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
