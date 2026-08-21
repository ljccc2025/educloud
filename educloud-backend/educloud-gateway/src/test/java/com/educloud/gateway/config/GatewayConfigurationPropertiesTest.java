package com.educloud.gateway.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayConfigurationPropertiesTest {

    private static jakarta.validation.ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void rejectsMissingMultipleAndInvalidSecuritySettings() {
        GatewaySecurityProperties missingJwks = validSecurity();
        missingJwks.setJwksJson(null);
        assertThat(messages(missingJwks)).anyMatch(message -> message.contains("exactly one JWKS source"));

        GatewaySecurityProperties multipleJwks = validSecurity();
        multipleJwks.setJwksLocation(new ByteArrayResource("public".getBytes(StandardCharsets.UTF_8)));
        assertThat(messages(multipleJwks)).anyMatch(message -> message.contains("exactly one JWKS source"));

        GatewaySecurityProperties blankIssuer = validSecurity();
        blankIssuer.setIssuer(" ");
        assertThat(paths(blankIssuer)).contains("issuer");

        GatewaySecurityProperties excessiveSkew = validSecurity();
        excessiveSkew.setClockSkew(Duration.ofSeconds(121));
        assertThat(messages(excessiveSkew)).anyMatch(message -> message.contains("clockSkew"));
    }

    @Test
    void rejectsInvalidEnvironmentNacosAndSecretSettings() {
        assertThat(paths(new GatewayRuntimeProperties("Prod_East"))).contains("environment");

        GatewayNacosClientProperties nacos = new GatewayNacosClientProperties(
                "127.0.0.1:8848", "local", "EDUCLOUD_GATEWAY", "EDUCLOUD_SERVICES", "", "");
        assertThat(paths(nacos)).contains("username", "password");
        GatewayNacosClientProperties printable = new GatewayNacosClientProperties(
                "127.0.0.1:8848", "local", "EDUCLOUD_GATEWAY", "EDUCLOUD_SERVICES",
                "educloud_gateway", "super-secret");
        assertThat(printable.toString()).contains("[REDACTED]").doesNotContain("super-secret");

        GatewayRateLimitProperties shortSecret = validRateLimit();
        shortSecret.setHmacSecretBase64(Base64.getEncoder().encodeToString(new byte[31]));
        assertThat(messages(shortSecret)).anyMatch(message -> message.contains("at least 32 bytes"));

        GatewayRateLimitProperties invalidBase64 = validRateLimit();
        invalidBase64.setHmacSecretBase64("not-base64!");
        assertThat(messages(invalidBase64)).anyMatch(message -> message.contains("at least 32 bytes"));

        GatewayRateLimitProperties zeroRate = validRateLimit();
        zeroRate.setOrdinary(new GatewayRateLimitProperties.Bucket(0, Duration.ofSeconds(1), 1));
        assertThat(messages(zeroRate)).anyMatch(message -> message.contains("positive"));

        GatewayRateLimitProperties excessiveRate = validRateLimit();
        excessiveRate.setOrdinary(new GatewayRateLimitProperties.Bucket(
                1_000_001, Duration.ofSeconds(1), 1_000_001));
        assertThat(messages(excessiveRate)).anyMatch(message -> message.contains("1000000"));

        GatewayRateLimitProperties excessiveBurst = validRateLimit();
        excessiveBurst.setOrdinary(new GatewayRateLimitProperties.Bucket(
                1, Duration.ofSeconds(1), 1_000_001));
        assertThat(messages(excessiveBurst)).anyMatch(message -> message.contains("1000000"));

        GatewayRateLimitProperties subMillisecondPeriod = validRateLimit();
        subMillisecondPeriod.setOrdinary(new GatewayRateLimitProperties.Bucket(
                1, Duration.ofNanos(1), 1));
        assertThat(messages(subMillisecondPeriod)).anyMatch(message -> message.contains("1 millisecond"));

        GatewayRateLimitProperties excessivePeriod = validRateLimit();
        excessivePeriod.setOrdinary(new GatewayRateLimitProperties.Bucket(
                1, Duration.ofDays(1).plusMillis(1), 1));
        assertThat(messages(excessivePeriod)).anyMatch(message -> message.contains("24 hours"));

        GatewayRateLimitProperties excessiveExpiry = validRateLimit();
        excessiveExpiry.setOrdinary(new GatewayRateLimitProperties.Bucket(
                1, Duration.ofHours(24), 8));
        assertThat(messages(excessiveExpiry)).anyMatch(message -> message.contains("7 days"));
    }

    @Test
    void rejectsUnsafeOriginsProxyAndResourceBounds() {
        GatewayWebProperties wildcard = validWeb();
        wildcard.setAllowedOrigins(List.of("*"));
        assertThat(messages(wildcard)).anyMatch(message -> message.contains("wildcard"));

        GatewayWebProperties invalidCidr = validWeb();
        invalidCidr.setTrustedProxyCidrs(List.of("10.0.0/8"));
        assertThat(paths(invalidCidr)).contains("trustedProxyCidrsValid");

        GatewayWebProperties zeroHops = validWeb();
        zeroHops.setTrustedProxyHops(0);
        assertThat(paths(zeroHops)).contains("trustedProxyHops");

        GatewayWebProperties oversized = validWeb();
        oversized.setHeaderLimit(DataSize.ofKilobytes(65));
        oversized.setConnectTimeout(Duration.ofSeconds(31));
        assertThat(paths(oversized)).contains("resourceBoundsValid");
    }

    @Test
    void acceptsLegalLocalConfigurationAndEnforcesCrossBeanRules() {
        assertThat(violations(validSecurity())).isEmpty();
        assertThat(violations(new GatewayRuntimeProperties("local"))).isEmpty();
        assertThat(violations(validRateLimit())).isEmpty();
        assertThat(violations(validWeb())).isEmpty();
        assertThat(violations(new GatewayNacosClientProperties(
                "127.0.0.1:8848", "local", "EDUCLOUD_GATEWAY", "EDUCLOUD_SERVICES",
                "educloud_gateway", "super-secret"))).isEmpty();

        GatewayConfigurationValidator.validate(
                new GatewayRuntimeProperties("local"), validWeb(), "127.0.0.1");

        assertThatThrownBy(() -> GatewayConfigurationValidator.validate(
                new GatewayRuntimeProperties("prod"), validWeb(), "127.0.0.1"))
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> GatewayConfigurationValidator.validate(
                new GatewayRuntimeProperties("local"), validWeb(), "0.0.0.0"))
                .hasMessageContaining("internal management address");
        assertThatThrownBy(() -> GatewayConfigurationValidator.validate(
                new GatewayRuntimeProperties("local"), validWeb(), "8.8.8.8"))
                .hasMessageContaining("internal management address");
    }

    @Test
    void requiresTheValidatedTimeoutsToMatchTheActualGatewayHttpClient() {
        GatewayWebProperties web = validWeb();
        HttpClientProperties matching = new HttpClientProperties();
        matching.setConnectTimeout(2000);
        matching.setResponseTimeout(Duration.ofSeconds(15));

        GatewayConfigurationValidator.validateHttpClient(web, matching);

        HttpClientProperties mismatchedConnect = new HttpClientProperties();
        mismatchedConnect.setConnectTimeout(3000);
        mismatchedConnect.setResponseTimeout(Duration.ofSeconds(15));
        assertThatThrownBy(() -> GatewayConfigurationValidator.validateHttpClient(
                web, mismatchedConnect))
                .hasMessageContaining("connect timeout");

        HttpClientProperties mismatchedResponse = new HttpClientProperties();
        mismatchedResponse.setConnectTimeout(2000);
        mismatchedResponse.setResponseTimeout(Duration.ofSeconds(30));
        assertThatThrownBy(() -> GatewayConfigurationValidator.validateHttpClient(
                web, mismatchedResponse))
                .hasMessageContaining("response timeout");
    }

    private static GatewaySecurityProperties validSecurity() {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.setJwksJson("{\"keys\":[]}");
        properties.setIssuer("https://identity.educloud.local");
        return properties;
    }

    private static GatewayRateLimitProperties validRateLimit() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties();
        properties.setHmacSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        return properties;
    }

    private static GatewayWebProperties validWeb() {
        GatewayWebProperties properties = new GatewayWebProperties();
        properties.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:5174",
                "http://localhost:5175",
                "http://127.0.0.1:5173",
                "http://127.0.0.1:5174",
                "http://127.0.0.1:5175"));
        properties.setTrustedProxyCidrs(List.of("10.0.0.0/8", "fd00::/8"));
        return properties;
    }

    private static Set<ConstraintViolation<Object>> violations(Object bean) {
        return validator.validate(bean);
    }

    private static Set<String> paths(Object bean) {
        return violations(bean).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static Set<String> messages(Object bean) {
        return violations(bean).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
