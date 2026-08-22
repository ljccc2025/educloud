package com.educloud.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestIdPolicyTest {

    private static final UUID GENERATED = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private final RequestIdPolicy policy = new RequestIdPolicy(() -> GENERATED);

    @Test
    void preservesAValidClientRequestId() {
        assertThat(policy.resolve("client.req_1-2")).isEqualTo("client.req_1-2");
    }

    @Test
    void replacesMissingOrInvalidValues() {
        assertThat(policy.resolve(null)).isEqualTo(GENERATED.toString());
        assertThat(policy.resolve("")).isEqualTo(GENERATED.toString());
        assertThat(policy.resolve("contains space")).isEqualTo(GENERATED.toString());
        assertThat(policy.resolve("x".repeat(65))).isEqualTo(GENERATED.toString());
        assertThat(policy.resolve("x".repeat(40))).isEqualTo("x".repeat(36));
        assertThat(policy.resolve("中文")).isEqualTo(GENERATED.toString());
    }
}
