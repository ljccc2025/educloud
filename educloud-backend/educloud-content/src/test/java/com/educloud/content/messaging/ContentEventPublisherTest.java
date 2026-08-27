package com.educloud.content.messaging;

import com.educloud.common.web.RequestContextAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ContentEventPublisher 单测（角色化动态流阶段 2）：验证各事件方法经
 * {@link OutboxWriter#write} 落库的信封字段与 payload 内容。
 */
class ContentEventPublisherTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 8, 27, 10, 0, 0);

    private OutboxWriter outboxWriter;
    private ObjectMapper objectMapper;
    private RequestContextAccessor requestContextAccessor;
    private ContentEventPublisher publisher;

    /** 捕获最近一次 OutboxWriter.write 调用参数。 */
    private record CapturedWrite(
            String aggregateType,
            String aggregateId,
            String eventType,
            int eventVersion,
            long aggregateVersion,
            String payloadJson,
            String requestId,
            String traceId) {
    }

    private CapturedWrite captured;

    @BeforeEach
    void setUp() {
        outboxWriter = mock(OutboxWriter.class);
        objectMapper = new ObjectMapper();
        requestContextAccessor = mock(RequestContextAccessor.class);
        when(requestContextAccessor.requestId()).thenReturn("req-1");
        when(requestContextAccessor.traceId()).thenReturn(Optional.of("trace-1"));
        doAnswer(invocation -> {
            captured = new CapturedWrite(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2),
                    invocation.getArgument(3),
                    invocation.getArgument(4),
                    invocation.getArgument(5),
                    invocation.getArgument(6),
                    invocation.getArgument(7));
            return null;
        }).when(outboxWriter).write(anyString(), anyString(), anyString(), anyInt(), anyLong(), any(), any(), any());
        publisher = new ContentEventPublisher(outboxWriter, objectMapper, requestContextAccessor);
    }

    @Test
    void assignmentSubmittedWritesOutboxRowWithEnvelopeFieldsAndPayload() throws Exception {
        publisher.assignmentSubmitted("asg-001", "第三章作业", "c_1001", 77L, 1L, OCCURRED_AT);

        assertThat(captured.aggregateType()).isEqualTo("Assignment");
        assertThat(captured.aggregateId()).isEqualTo("asg-001");
        assertThat(captured.eventType()).isEqualTo("AssignmentSubmitted");
        assertThat(captured.eventVersion()).isEqualTo(1);
        assertThat(captured.aggregateVersion()).isEqualTo(1L);
        assertThat(captured.requestId()).isEqualTo("req-1");
        assertThat(captured.traceId()).isEqualTo("trace-1");

        JsonNode payload = objectMapper.readTree(captured.payloadJson());
        assertThat(payload.get("assignmentId").asText()).isEqualTo("asg-001");
        assertThat(payload.get("assignmentTitle").asText()).isEqualTo("第三章作业");
        assertThat(payload.get("courseId").asText()).isEqualTo("c_1001");
        assertThat(payload.get("studentId").asLong()).isEqualTo(77L);
        assertThat(payload.get("submittedAt").asText()).isEqualTo(OCCURRED_AT.toString());
    }

    @Test
    void assignmentGradedWritesOutboxRowWithScoreAndFeedback() throws Exception {
        publisher.assignmentGraded("asg-001", "第三章作业", "c_1001", 77L, 95, "优秀", 1L, OCCURRED_AT);

        assertThat(captured.aggregateType()).isEqualTo("Assignment");
        assertThat(captured.aggregateId()).isEqualTo("asg-001");
        assertThat(captured.eventType()).isEqualTo("AssignmentGraded");

        JsonNode payload = objectMapper.readTree(captured.payloadJson());
        assertThat(payload.get("assignmentId").asText()).isEqualTo("asg-001");
        assertThat(payload.get("assignmentTitle").asText()).isEqualTo("第三章作业");
        assertThat(payload.get("courseId").asText()).isEqualTo("c_1001");
        assertThat(payload.get("studentId").asLong()).isEqualTo(77L);
        assertThat(payload.get("score").asInt()).isEqualTo(95);
        assertThat(payload.get("feedback").asText()).isEqualTo("优秀");
        assertThat(payload.get("gradedAt").asText()).isEqualTo(OCCURRED_AT.toString());
    }

    @Test
    void courseCompletedWritesOutboxRow() throws Exception {
        publisher.courseCompleted(1001L, 77L, 2L, OCCURRED_AT);

        assertThat(captured.aggregateType()).isEqualTo("LearningProgress");
        assertThat(captured.aggregateId()).isEqualTo("1001");
        assertThat(captured.eventType()).isEqualTo("CourseCompleted");
        assertThat(captured.aggregateVersion()).isEqualTo(2L);

        JsonNode payload = objectMapper.readTree(captured.payloadJson());
        assertThat(payload.get("courseId").asLong()).isEqualTo(1001L);
        assertThat(payload.get("studentId").asLong()).isEqualTo(77L);
        assertThat(payload.get("completedAt").asText()).isEqualTo(OCCURRED_AT.toString());
    }

    @Test
    void certificateIssuedWritesOutboxRowWithTeacherId() throws Exception {
        publisher.certificateIssued("CERT-2026-0001", 1001L, 77L, 2001L, 3L, OCCURRED_AT);

        assertThat(captured.aggregateType()).isEqualTo("Certificate");
        assertThat(captured.aggregateId()).isEqualTo("CERT-2026-0001");
        assertThat(captured.eventType()).isEqualTo("CertificateIssued");

        JsonNode payload = objectMapper.readTree(captured.payloadJson());
        assertThat(payload.get("certificateNo").asText()).isEqualTo("CERT-2026-0001");
        assertThat(payload.get("courseId").asLong()).isEqualTo(1001L);
        assertThat(payload.get("studentId").asLong()).isEqualTo(77L);
        assertThat(payload.get("teacherId").asLong()).isEqualTo(2001L);
        assertThat(payload.get("issuedAt").asText()).isEqualTo(OCCURRED_AT.toString());
    }

    @Test
    void absentTraceIdIsPassedAsNull() {
        when(requestContextAccessor.traceId()).thenReturn(Optional.empty());

        publisher.assignmentSubmitted("asg-001", "作业", "c_1001", 77L, 1L, OCCURRED_AT);

        verify(outboxWriter).write(
                eq("Assignment"), eq("asg-001"), eq("AssignmentSubmitted"),
                eq(1), eq(1L), anyString(), eq("req-1"), eq(null));
        assertThat(captured.traceId()).isNull();
    }
}
