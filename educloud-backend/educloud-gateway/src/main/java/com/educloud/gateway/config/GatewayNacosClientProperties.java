package com.educloud.gateway.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("educloud.gateway.nacos")
@Validated
public record GatewayNacosClientProperties(
        @NotBlank String serverAddr,
        @NotBlank String namespace,
        @NotBlank String configGroup,
        @NotBlank String discoveryGroup,
        @NotBlank String username,
        @NotBlank String password) {

    @Override
    public String toString() {
        return "GatewayNacosClientProperties[serverAddr=" + serverAddr
                + ", namespace=" + namespace
                + ", configGroup=" + configGroup
                + ", discoveryGroup=" + discoveryGroup
                + ", username=" + username
                + ", password=[REDACTED]]";
    }
}
