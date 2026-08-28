package com.educloud.content.service;

import com.educloud.content.dto.request.AssignmentSubmitRequest;
import com.educloud.content.dto.response.AssignmentResponse;
import com.educloud.content.messaging.ContentEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AssignmentService 领域事件发布单测（角色化动态流阶段 2）：
 * 提交作业发布 AssignmentSubmitted、批改发布 AssignmentGraded（含 score/feedback）；
 * 事件发布失败不阻断业务主流程（Redis 读写成功即视为成功）。
 */
class AssignmentServiceEventTest {

    private static final String ASSIGNMENT_ID = "asg-001";
    private static final String ASSIGNMENT_JSON = """
            {"id":"asg-001","courseId":"c_1001","courseTitle":"Spring Boot 微服务实践",
            "title":"第三章作业","totalScore":100,"status":"PUBLISHED"}""";
    private static final String SUBMISSION_KEY = "educloud:submissions_by_id:sub-77-asg-001";
    private static final String SUBMISSION_JSON = """
            {"id":"sub-77-asg-001","assignmentId":"asg-001","studentId":"77",
            "studentName":"李明","content":"已完成","status":"SUBMITTED"}""";

    private StringRedisTemplate redisTemplate;
    private ObjectMapper objectMapper;
    private ContentEventPublisher contentEventPublisher;
    private AssignmentService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class, Mockito.RETURNS_DEEP_STUBS);
        objectMapper = new ObjectMapper();
        contentEventPublisher = mock(ContentEventPublisher.class);
        service = new AssignmentService(redisTemplate, objectMapper, null, contentEventPublisher,
                mock(CourseClient.class));

        when(redisTemplate.opsForHash().get("educloud:assignments:map", ASSIGNMENT_ID))
                .thenReturn(ASSIGNMENT_JSON);
    }

    private AssignmentSubmitRequest submitRequest() {
        AssignmentSubmitRequest request = new AssignmentSubmitRequest();
        request.setContent("已完成作业内容");
        request.setStudentName("李明");
        return request;
    }

    @Test
    void submitAssignmentPublishesAssignmentSubmittedEvent() {
        AssignmentResponse response = service.submitAssignment(ASSIGNMENT_ID, submitRequest(), 77L, "李明");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("SUBMITTED");
        verify(contentEventPublisher).assignmentSubmitted(
                eq(ASSIGNMENT_ID), eq("第三章作业"), eq("c_1001"), eq(77L), eq(1L),
                any(LocalDateTime.class));
    }

    @Test
    void submitAssignmentSucceedsWhenEventPublishingFails() {
        doThrow(new RuntimeException("outbox unavailable"))
                .when(contentEventPublisher).assignmentSubmitted(
                        anyString(), anyString(), anyString(), anyLong(), anyLong(), any());

        AssignmentResponse response = service.submitAssignment(ASSIGNMENT_ID, submitRequest(), 77L, "李明");

        // 容错：事件发布失败仅记日志，提交主流程正常返回。
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    void gradeSubmissionPublishesAssignmentGradedEventWithScore() {
        when(redisTemplate.opsForValue().get(SUBMISSION_KEY)).thenReturn(SUBMISSION_JSON);

        service.gradeSubmission("sub-77-asg-001", null, null, 95, "优秀");

        verify(contentEventPublisher).assignmentGraded(
                eq(ASSIGNMENT_ID), eq("第三章作业"), eq("c_1001"), eq(77L), eq(95), eq("优秀"),
                eq(1L), any(LocalDateTime.class));
    }

    @Test
    void gradeSubmissionSucceedsWhenEventPublishingFails() {
        when(redisTemplate.opsForValue().get(SUBMISSION_KEY)).thenReturn(SUBMISSION_JSON);
        doThrow(new RuntimeException("outbox unavailable"))
                .when(contentEventPublisher).assignmentGraded(
                        anyString(), any(), any(), anyLong(), any(), any(), anyLong(), any());

        // 容错：事件发布失败仅记日志，批改主流程不抛异常。
        service.gradeSubmission("sub-77-asg-001", null, null, 95, "优秀");

        verify(contentEventPublisher).assignmentGraded(
                eq(ASSIGNMENT_ID), any(), any(), eq(77L), eq(95), eq("优秀"), eq(1L), any());
    }

    @Test
    void gradeSubmissionWithoutSubmissionDoesNotPublishEvent() {
        service.gradeSubmission("sub-unknown", null, null, 90, "无提交");

        Mockito.verifyNoInteractions(contentEventPublisher);
    }
}
