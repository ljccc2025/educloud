package com.educloud.common.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void serializesTheStableEventContractInDeclaredOrder() throws Exception {
        var envelope = envelope(2, 99, 7);

        assertThat(objectMapper.writeValueAsString(envelope)).isEqualTo(
                "{\"eventId\":\"event-1\",\"eventType\":\"user.created\",\"eventVersion\":2,"
                        + "\"sourceService\":\"educloud-user\",\"sourceSequence\":99,"
                        + "\"aggregateType\":\"User\",\"aggregateId\":\"user-1\","
                        + "\"aggregateVersion\":7,\"occurredAt\":\"2026-08-20T08:00:00Z\","
                        + "\"requestId\":\"req-1\",\"traceId\":\"trace-1\","
                        + "\"data\":{\"name\":\"Alice\"}}");
    }

    @Test
    void keepsSchemaSourceAndAggregateVersionsIndependent() {
        var envelope = envelope(2, 99, 7);

        assertThat(envelope.eventVersion()).isEqualTo(2);
        assertThat(envelope.sourceSequence()).isEqualTo(99);
        assertThat(envelope.aggregateVersion()).isEqualTo(7);
    }

    @Test
    void rejectsMissingRequiredFieldsAndInvalidVersions() {
        assertThatThrownBy(() -> new EventEnvelope<>(
                        " ", "type", 1, "source", 0, "Aggregate", "id", 0,
                        Instant.now(), "req", null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventEnvelope<>(
                        "event", "type", 0, "source", 0, "Aggregate", "id", 0,
                        Instant.now(), "req", null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventEnvelope<>(
                        "event", "type", 1, "source", -1, "Aggregate", "id", 0,
                        Instant.now(), "req", null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventEnvelope<>(
                        "event", "type", 1, "source", 0, "Aggregate", "id", -1,
                        Instant.now(), "req", null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventEnvelope<>(
                        "event", "type", 1, "source", 0, "Aggregate", "id", 0,
                        null, "req", null, Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EventEnvelope<>(
                        "event", "type", 1, "source", 0, "Aggregate", "id", 0,
                        Instant.now(), "req", null, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static EventEnvelope<Map<String, String>> envelope(
            int eventVersion,
            long sourceSequence,
            long aggregateVersion) {
        return new EventEnvelope<>(
                "event-1",
                "user.created",
                eventVersion,
                "educloud-user",
                sourceSequence,
                "User",
                "user-1",
                aggregateVersion,
                Instant.parse("2026-08-20T08:00:00Z"),
                "req-1",
                "trace-1",
                Map.of("name", "Alice"));
    }
}
