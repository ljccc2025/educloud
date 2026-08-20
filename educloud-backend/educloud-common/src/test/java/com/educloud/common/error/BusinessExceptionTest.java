package com.educloud.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

    @Test
    void usesTheStableDefaultMessageWhenNoOverrideIsProvided() {
        var exception = new BusinessException(CommonErrorCode.VERSION_CONFLICT);

        assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.VERSION_CONFLICT);
        assertThat(exception.getMessage()).isEqualTo("Resource version conflict");
        assertThat(exception.details()).isNull();
    }

    @Test
    void carriesOnlyTypedSafeDetailsAndKeepsTheCauseOnTheExceptionChain() {
        var cause = new IllegalStateException("internal diagnostic");
        var details = new SafeDetails("expected-v2");

        var exception = new BusinessException(
                CommonErrorCode.VERSION_CONFLICT,
                "The resource changed",
                details,
                cause);

        assertThat(exception.details()).isSameAs(details);
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getMessage()).doesNotContain("internal diagnostic");
    }

    private record SafeDetails(String expectedVersion) implements ErrorDetails {}
}
