package com.educloud.analytics.messaging;

import com.educloud.analytics.config.RabbitMqConfig;
import com.educloud.analytics.service.ActivityFeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 角色化动态流消费者（规格 2026-08-27-activity-feed-certificate-design.md §5）。
 *
 * <p>订阅课程/支付/内容领域事件交换机上的动态流专用队列，解析事件后映射为
 * {@code activity_feed} 记录（事件 → 动态映射见规格 §4.2）：</p>
 * <ul>
 *   <li>选课事件（{@code EnrollmentCreated}/{@code PaymentSuccess}/{@code OrderPaid}）
 *       → 学生 {@code ENROLLED} + 教师 {@code STUDENT_ENROLLED}（教师从事件课程归属字段解析）</li>
 *   <li>作业批改事件（{@code AssignmentGraded}）→ 学生 {@code ASSIGNMENT_GRADED}</li>
 *   <li>课程发布事件（{@code CoursePublished}）→ 教师 {@code COURSE_PUBLISHED}；
 *       兼容映射 {@code CourseCreated} → {@code COURSE_CREATED}</li>
 *   <li>其余事件类型暂未映射：记日志跳过，不阻断</li>
 * </ul>
 *
 * <p>容错（规格 §9）：监听方法接收原始 {@link Message} 手动解析，兼容
 * EventEnvelope（payload 在 {@code data} 内）与扁平 DTO 两种结构；任何解析/映射
 * 异常仅记日志不抛出，避免坏消息无限重投。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityFeedConsumer {

    private static final String ROLE_STUDENT = "STUDENT";
    private static final String ROLE_TEACHER = "TEACHER";
    private static final DateTimeFormatter SPACE_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ActivityFeedService activityFeedService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqConfig.QUEUE_ACTIVITY_FEED_COURSE)
    public void onCourseEvent(Message message) {
        handle(message, "educloud-course");
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_ACTIVITY_FEED_PAYMENT)
    public void onPaymentEvent(Message message) {
        handle(message, "educloud-payment");
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_ACTIVITY_FEED_CONTENT)
    public void onContentEvent(Message message) {
        handle(message, "educloud-content");
    }

    /** 解析单条消息并映射为动态；包级可见便于单测。 */
    void handle(Message message, String sourceHint) {
        if (message == null || message.getBody() == null || message.getBody().length == 0) {
            log.warn("ActivityFeedConsumer received empty message from [{}], skipped", sourceHint);
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(message.getBody());
            if (root == null || !root.isObject()) {
                log.warn("ActivityFeedConsumer received non-object JSON from [{}], skipped", sourceHint);
                return;
            }
            String eventType = text(root, "eventType");
            String eventId = text(root, "eventId");
            LocalDateTime occurredAt = parseTime(text(root, "occurredAt"));
            if (eventType == null || eventType.isBlank()) {
                log.warn("ActivityFeedConsumer received event without eventType from [{}], skipped: eventId={}",
                        sourceHint, eventId);
                return;
            }

            switch (eventType.toLowerCase()) {
                case "enrollmentcreated", "paymentsuccess", "orderpaid" -> mapEnrollment(root, eventId, occurredAt);
                case "assignmentgraded", "assignment.graded" -> mapAssignmentGraded(root, eventId, occurredAt);
                case "coursepublished" -> mapCourseLifecycle(root, eventId, occurredAt, "COURSE_PUBLISHED");
                case "coursecreated" -> mapCourseLifecycle(root, eventId, occurredAt, "COURSE_CREATED");
                default -> log.info("ActivityFeedConsumer skipped unmapped event type [{}] from [{}]: eventId={}",
                        eventType, sourceHint, eventId);
            }
        } catch (Exception e) {
            log.error("ActivityFeedConsumer failed to process message from [{}], skipped", sourceHint, e);
        }
    }

    /** 选课/支付成功 → 学生报名动态 + 教师侧学员报名动态。 */
    private void mapEnrollment(JsonNode root, String eventId, LocalDateTime occurredAt) {
        String studentId = text(root, "studentId", "userId");
        String courseId = text(root, "courseId");
        String courseTitle = text(root, "courseTitle", "title");
        String teacherId = text(root, "teacherId");

        Map<String, Object> extra = new LinkedHashMap<>();
        putIfPresent(extra, "source", root, "source");
        putIfPresent(extra, "orderNo", root, "orderNo");
        putIfPresent(extra, "amountCents", root, "amountCents");

        if (studentId == null || studentId.isBlank()) {
            log.warn("Enrollment event without studentId skipped: eventId={}", eventId);
            return;
        }
        activityFeedService.recordActivity(
                studentId, ROLE_STUDENT, "ENROLLED", "COURSE", courseId, courseTitle,
                extra.isEmpty() ? null : extra,
                suffix(eventId, "ENROLLED"), occurredAt);

        if (teacherId != null && !teacherId.isBlank()) {
            Map<String, Object> teacherExtra = new LinkedHashMap<>();
            teacherExtra.put("studentId", studentId);
            activityFeedService.recordActivity(
                    teacherId, ROLE_TEACHER, "STUDENT_ENROLLED", "COURSE", courseId, courseTitle,
                    teacherExtra, suffix(eventId, "STUDENT_ENROLLED"), occurredAt);
        } else {
            // 课程归属教师解析依赖事件携带 teacherId（阶段 2 补发事件后补齐），暂记日志跳过教师行动态
            log.warn("Enrollment event without teacherId, teacher-side activity skipped: eventId={}, courseId={}",
                    eventId, courseId);
        }
    }

    /** 作业批改 → 学生作业批改动态。 */
    private void mapAssignmentGraded(JsonNode root, String eventId, LocalDateTime occurredAt) {
        String studentId = text(root, "studentId", "userId");
        if (studentId == null || studentId.isBlank()) {
            log.warn("AssignmentGraded event without studentId/userId skipped: eventId={}", eventId);
            return;
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        putIfPresent(extra, "score", root, "score");
        putIfPresent(extra, "feedback", root, "feedback");
        activityFeedService.recordActivity(
                studentId, ROLE_STUDENT, "ASSIGNMENT_GRADED", "ASSIGNMENT",
                text(root, "assignmentId"), text(root, "assignmentTitle", "title"),
                extra.isEmpty() ? null : extra,
                suffix(eventId, "ASSIGNMENT_GRADED"), occurredAt);
    }

    /** 课程发布/创建 → 教师教学动态（教师从事件课程归属字段解析）。 */
    private void mapCourseLifecycle(JsonNode root, String eventId, LocalDateTime occurredAt, String actionType) {
        String teacherId = text(root, "teacherId");
        if (teacherId == null || teacherId.isBlank()) {
            // 现有 CoursePublished outbox payload 仅含 courseId/versionId，无 teacherId，
            // 待课程服务补发带教师归属的事件后可映射（阶段 2）
            log.warn("{} event without teacherId, teacher-side activity skipped: eventId={}, courseId={}",
                    actionType, eventId, text(root, "courseId"));
            return;
        }
        activityFeedService.recordActivity(
                teacherId, ROLE_TEACHER, actionType, "COURSE",
                text(root, "courseId"), text(root, "title", "courseTitle"),
                null, suffix(eventId, actionType), occurredAt);
    }

    /** 字段提取：优先顶层，其次 EventEnvelope 的 data 节点。 */
    private JsonNode field(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode value = root.get(name);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        JsonNode data = root.get("data");
        if (data != null && data.isObject()) {
            for (String name : names) {
                JsonNode value = data.get(name);
                if (value != null && !value.isNull()) {
                    return value;
                }
            }
        }
        return null;
    }

    private String text(JsonNode root, String... names) {
        JsonNode value = field(root, names);
        if (value == null) {
            return null;
        }
        String text = value.asText();
        return (text == null || text.isBlank()) ? null : text.trim();
    }

    private void putIfPresent(Map<String, Object> extra, String key, JsonNode root, String... names) {
        JsonNode value = field(root, names);
        if (value == null) {
            return;
        }
        if (value.isNumber()) {
            extra.put(key, value.numberValue());
        } else {
            String text = value.asText();
            if (text != null && !text.isBlank()) {
                extra.put(key, text);
            }
        }
    }

    /** 时间解析容错：LocalDateTime（ISO/空格分隔）与 Instant（UTC 转本地）均兼容，失败取当前时间。 */
    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            // try next format
        }
        try {
            return LocalDateTime.parse(value, SPACE_DATETIME);
        } catch (Exception ignored) {
            // try next format
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(value), ZoneId.systemDefault());
        } catch (Exception ignored) {
            log.warn("Unparseable occurredAt [{}], fallback to now", value);
            return LocalDateTime.now();
        }
    }

    /** 幂等键后缀：一个事件可派生多行动态（学生 + 教师），需区分唯一键。 */
    private String suffix(String eventId, String actionType) {
        return eventId == null || eventId.isBlank() ? null : eventId + "_" + actionType;
    }
}
