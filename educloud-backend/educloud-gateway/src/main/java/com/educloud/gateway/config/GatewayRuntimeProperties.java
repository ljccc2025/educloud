package com.educloud.gateway.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("educloud.gateway")
@Validated
public record GatewayRuntimeProperties(
        @NotBlank
        @Pattern(regexp = "[a-z0-9-]{1,32}", message = "environment must match [a-z0-9-]{1,32}")
        String environment) {
}
