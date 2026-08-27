package com.educloud.analytics.messaging;

import com.educloud.analytics.service.ActivityFeedService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * ActivityFeedConsumer 单测：事件 → 动态映射、角色映射、幂等键、解析失败容错。
 */
@ExtendWith(MockitoExtension.class)
class ActivityFeedConsumerTest {

    @Mock
    private ActivityFeedService activityFeedService;

    private ObjectMapper objectMapper;

    private ActivityFeedConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        consumer = new ActivityFeedConsumer(activityFeedService, objectMapper);
    }

    private static Message message(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    @Test
    @DisplayName("选课事件 → 学生 ENROLLED + 教师 STUDENT_ENROLLED 双动态")
    void testEnrollmentCreatedMapsBothRoles() {
        String json = """
                {
                  "eventId": "EVT_ENR_001",
                  "eventType": "EnrollmentCreated",
                  "courseId": "course_101",
                  "courseTitle": "Spring Cloud 微服务",
                  "studentId": "stu_1001",
                  "teacherId": "teacher_01",
                  "source": "FREE",
                  "occurredAt": "2026-08-27T10:00:00"
                }
                """;

        consumer.handle(message(json), "educloud-course");

        ArgumentCaptor<Map<String, Object>> extraCaptor = ArgumentCaptor.forClass(Map.class);
        verify(activityFeedService).recordActivity(
                eq("stu_1001"), eq("STUDENT"), eq("ENROLLED"), eq("COURSE"),
                eq("course_101"), eq("Spring Cloud 微服务"), extraCaptor.capture(),
                eq("EVT_ENR_001_ENROLLED"), eq(LocalDateTime.of(2026, 8, 27, 10, 0)));
        assertThat(extraCaptor.getValue()).containsEntry("source", "FREE");

        ArgumentCaptor<Map<String, Object>> teacherExtraCaptor = ArgumentCaptor.forClass(Map.class);
        verify(activityFeedService).recordActivity(
                eq("teacher_01"), eq("TEACHER"), eq("STUDENT_ENROLLED"), eq("COURSE"),
                eq("course_101"), eq("Spring Cloud 微服务"), teacherExtraCaptor.capture(),
                eq("EVT_ENR_001_STUDENT_ENROLLED"), eq(LocalDateTime.of(2026, 8, 27, 10, 0)));
        assertThat(teacherExtraCaptor.getValue()).containsEntry("studentId", "stu_1001");
    }

    @Test
    @DisplayName("支付成功事件同样映射为报名动态（付费选课）")
    void testPaymentSuccessMapsToEnrolled() {
        String json = """
                {
                  "eventId": "EVT_PAY_001",
                  "eventType": "PaymentSuccess",
                  "orderNo": "ORD_9901",
                  "courseId": "course_101",
                  "courseTitle": "Vue 3 中台",
                  "studentId": "stu_1002",
                  "teacherId": "teacher_02",
                  "amountCents": 19900,
                  "occurredAt": "2026-08-27T11:00:00"
                }
                """;

        consumer.handle(message(json), "educloud-payment");

        verify(activityFeedService).recordActivity(
                eq("stu_1002"), eq("STUDENT"), eq("ENROLLED"), any(), any(), any(), any(),
                eq("EVT_PAY_001_ENROLLED"), any());
        verify(activityFeedService).recordActivity(
                eq("teacher_02"), eq("TEACHER"), eq("STUDENT_ENROLLED"), any(), any(), any(), any(),
                eq("EVT_PAY_001_STUDENT_ENROLLED"), any());
    }

    @Test
    @DisplayName("EventEnvelope 结构（payload 在 data 内）同样可解析")
    void testEventEnvelopePayload() {
        String json = """
                {
                  "eventId": "EVT_ENV_001",
                  "eventType": "EnrollmentCreated",
                  "sourceService": "educloud-course",
                  "occurredAt": "2026-08-27T04:00:00Z",
                  "data": {
                    "courseId": "55",
                    "studentId": "77",
                    "source": "PAID"
                  }
                }
                """;

        consumer.handle(message(json), "educloud-course");

        // envelope 无 teacherId：仅学生动态，教师行动态记日志跳过
        verify(activityFeedService, times(1)).recordActivity(
                eq("77"), eq("STUDENT"), eq("ENROLLED"), eq("COURSE"),
                eq("55"), isNull(), any(), eq("EVT_ENV_001_ENROLLED"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("选课事件无教师归属 → 仅学生动态，不阻断")
    void testEnrollmentWithoutTeacherOnlyStudentRow() {
        String json = """
                {
                  "eventId": "EVT_ENR_002",
                  "eventType": "EnrollmentCreated",
                  "courseId": "course_102",
                  "studentId": "stu_1003"
                }
                """;

        consumer.handle(message(json), "educloud-course");

        verify(activityFeedService, times(1)).recordActivity(
                anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());
        verify(activityFeedService).recordActivity(
                eq("stu_1003"), eq("STUDENT"), eq("ENROLLED"), any(), any(), any(), isNull(),
                eq("EVT_ENR_002_ENROLLED"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("课程发布事件 → 教师 COURSE_PUBLISHED 动态")
    void testCoursePublishedMapsTeacherActivity() {
        String json = """
                {
                  "eventId": "EVT_CRS_001",
                  "eventType": "CoursePublished",
                  "courseId": "course_201",
                  "title": "Kubernetes 实战",
                  "teacherId": "teacher_03",
                  "occurredAt": "2026-08-27T09:30:00"
                }
                """;

        consumer.handle(message(json), "educloud-course");

        verify(activityFeedService).recordActivity(
                eq("teacher_03"), eq("TEACHER"), eq("COURSE_PUBLISHED"), eq("COURSE"),
                eq("course_201"), eq("Kubernetes 实战"), isNull(),
                eq("EVT_CRS_001_COURSE_PUBLISHED"), eq(LocalDateTime.of(2026, 8, 27, 9, 30)));
    }

    @Test
    @DisplayName("课程发布事件无教师归属 → 记日志跳过，不写动态")
    void testCoursePublishedWithoutTeacherSkipped() {
        String json = """
                {
                  "eventId": "EVT_CRS_002",
                  "eventType": "CoursePublished",
                  "courseId": "course_202"
                }
                """;

        consumer.handle(message(json), "educloud-course");

        verifyNoInteractions(activityFeedService);
    }

    @Test
    @DisplayName("作业批改事件 → 学生 ASSIGNMENT_GRADED 动态（含分数扩展字段）")
    void testAssignmentGradedMapsStudentActivity() {
        String json = """
                {
                  "eventId": "EVT_ASG_001",
                  "eventType": "AssignmentGraded",
                  "assignmentId": "asg_3001",
                  "assignmentTitle": "微服务模块打包",
                  "userId": "3001",
                  "courseId": "course_101",
                  "score": 95,
                  "feedback": "优秀",
                  "occurredAt": "2026-08-27 14:30:00"
                }
                """;

        consumer.handle(message(json), "educloud-content");

        ArgumentCaptor<Map<String, Object>> extraCaptor = ArgumentCaptor.forClass(Map.class);
        verify(activityFeedService).recordActivity(
                eq("3001"), eq("STUDENT"), eq("ASSIGNMENT_GRADED"), eq("ASSIGNMENT"),
                eq("asg_3001"), eq("微服务模块打包"), extraCaptor.capture(),
                eq("EVT_ASG_001_ASSIGNMENT_GRADED"), eq(LocalDateTime.of(2026, 8, 27, 14, 30)));
        assertThat(extraCaptor.getValue()).containsEntry("score", 95).containsEntry("feedback", "优秀");
    }

    @Test
    @DisplayName("暂未映射的事件类型（如内容修订发布）记日志跳过")
    void testUnmappedEventTypeSkipped() {
        String json = """
                {
                  "eventId": "EVT_CNT_001",
                  "eventType": "ContentRevisionPublished",
                  "courseId": "course_101"
                }
                """;

        consumer.handle(message(json), "educloud-content");

        verifyNoInteractions(activityFeedService);
    }

    @Test
    @DisplayName("非法 JSON 消息 → 容错不抛异常、不写动态")
    void testMalformedJsonTolerated() {
        consumer.handle(message("{not-valid-json!!"), "educloud-course");
        verifyNoInteractions(activityFeedService);
    }

    @Test
    @DisplayName("空消息与缺失 eventType → 容错跳过")
    void testEmptyMessageAndMissingEventTypeTolerated() {
        consumer.handle(null, "educloud-course");
        consumer.handle(message(""), "educloud-course");
        consumer.handle(message("{\"eventId\":\"EVT_X\"}"), "educloud-course");
        verifyNoInteractions(activityFeedService);
    }

    @Test
    @DisplayName("选课事件缺学员 → 跳过且不抛异常")
    void testEnrollmentWithoutStudentSkipped() {
        String json = """
                {
                  "eventId": "EVT_ENR_003",
                  "eventType": "EnrollmentCreated",
                  "courseId": "course_103"
                }
                """;

        consumer.handle(message(json), "educloud-course");

        verify(activityFeedService, never()).recordActivity(
                anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("缺失 occurredAt → 兜底当前时间，不阻断写入")
    void testMissingOccurredAtFallbackNow() {
        String json = """
                {
                  "eventId": "EVT_ENR_004",
                  "eventType": "EnrollmentCreated",
                  "courseId": "course_104",
                  "studentId": "stu_1004",
                  "teacherId": "teacher_04"
                }
                """;

        consumer.handle(message(json), "educloud-course");

        verify(activityFeedService).recordActivity(
                eq("stu_1004"), eq("STUDENT"), eq("ENROLLED"), any(), any(), any(), any(),
                eq("EVT_ENR_004_ENROLLED"), any(LocalDateTime.class));
    }
}
