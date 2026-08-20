package com.educloud.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void serializesTheStableResponseContract() throws Exception {
        var response = new ApiResponse<>(
                "SUCCESS",
                "OK",
                Map.of("id", "42"),
                "req-1",
                Instant.parse("2026-08-20T08:00:00Z"));

        assertThat(objectMapper.writeValueAsString(response)).isEqualTo(
                "{\"code\":\"SUCCESS\",\"message\":\"OK\",\"data\":{\"id\":\"42\"},"
                        + "\"requestId\":\"req-1\",\"timestamp\":\"2026-08-20T08:00:00Z\"}");
    }

    @Test
    void includesNullDataInTheContract() throws Exception {
        var response = new ApiResponse<Void>(
                "SUCCESS",
                "OK",
                null,
                "req-2",
                Instant.parse("2026-08-20T08:00:00Z"));

        assertThat(objectMapper.writeValueAsString(response)).contains("\"data\":null");
    }

    @Test
    void rejectsMissingContractMetadata() {
        var instant = Instant.parse("2026-08-20T08:00:00Z");

        assertThatThrownBy(() -> new ApiResponse<>(" ", "OK", null, "req-1", instant))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiResponse<>("SUCCESS", null, null, "req-1", instant))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ApiResponse<>("SUCCESS", "OK", null, " ", instant))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiResponse<>("SUCCESS", "OK", null, "req-1", null))
                .isInstanceOf(NullPointerException.class);
    }
}
