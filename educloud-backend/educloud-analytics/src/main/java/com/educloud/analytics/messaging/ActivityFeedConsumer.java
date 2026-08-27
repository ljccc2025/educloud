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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 角色化动态流消费者（规格 2026-08-27-activity-feed-certificate-design.md §5）。
 *
 * <p>订阅课程/支付/内容/作业领域事件交换机上的各域专用队列，解析事件后映射为
 * {@code activity_feed} 记录（事件 → 动态映射见规格 §4.2）：</p>
 * <ul>
 *   <li>选课事件（{@code EnrollmentCreated}/{@code PaymentSuccess}/{@code OrderPaid}）
 *       → 学生 {@code ENROLLED} + 教师 {@code STUDENT_ENROLLED}（教师从事件课程归属字段解析）</li>
 *   <li>作业批改事件（{@code AssignmentGraded}，routing key {@code assignment.graded}，
 *       发布在全域总线 {@code educloud.events}）→ 学生 {@code ASSIGNMENT_GRADED}</li>
 *   <li>课程发布事件（{@code CoursePublished}）→ 教师 {@code COURSE_PUBLISHED}；
 *       兼容映射 {@code CourseCreated} → {@code COURSE_CREATED}</li>
 *   <li>其余事件类型暂未映射：记日志跳过，不阻断</li>
 * </ul>
 *
 * <p>容错（规格 §9）：监听方法接收原始 {@link Message} 手动解析，兼容
 * EventEnvelope（payload 在 {@code data} 内）与扁平 DTO 两种结构；任何解析/映射
 * 异常仅记日志不抛出，避免坏消息无限重投。</p>
 *
 * <p>重复 eventId 处理：同一事件多次到达（重投/多队列镜像）时，先经内存级
 * 有界 LRU 缓存短路跳过，再由 {@code activity_feed.uk_source_event} 唯一约束兼底，
 * 保证仅记录一条动态。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityFeedConsumer {

    private static final String ROLE_STUDENT = "STUDENT";
    private static final String ROLE_TEACHER = "TEACHER";
    private static final DateTimeFormatter SPACE_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 近期已处理 eventId 缓存容量（有界，超出按最久未访问淘汰）。 */
    private static final int SEEN_EVENT_IDS_CAPACITY = 1024;

    private final ActivityFeedService activityFeedService;
    private final ObjectMapper objectMapper;

    /** 有界 LRU：同一 eventId 短时间内重复到达时直接短路，避免穿透到库。 */
    private final Map<String, Boolean> seenEventIds = Collections.synchronizedMap(
            new LinkedHashMap<String, Boolean>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > SEEN_EVENT_IDS_CAPACITY;
                }
            });

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
        handle(message, "educloud-content", null);
    }

    /** 作业批改专用队列：绑定全域总线 educloud.events 的 assignment.graded 路由。 */
    @RabbitListener(queues = RabbitMqConfig.QUEUE_ACTIVITY_FEED_ASSIGNMENT)
    public void onAssignmentEvent(Message message) {
        // 该路由消息可能不携 eventType 字段（如 notification 同源的 AssignmentGradedEvent 扁平结构），
        // 路由键本身已确定事件语义，缺省时按 AssignmentGraded 处理。
        handle(message, "educloud-content", "AssignmentGraded");
    }

    /** 解析单条消息并映射为动态；包级可见便于单测。 */
    void handle(Message message, String sourceHint) {
        handle(message, sourceHint, null);
    }

    /**
     * 解析单条消息并映射为动态；包级可见便于单测。
     *
     * @param defaultEventType 消息缺省 eventType 时的兑底类型（可为 null）
     */
    void handle(Message message, String sourceHint, String defaultEventType) {
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
            if ((eventType == null || eventType.isBlank()) && defaultEventType != null) {
                eventType = defaultEventType;
            }
            LocalDateTime occurredAt = parseTime(text(root, "occurredAt"));
            if (eventType == null || eventType.isBlank()) {
                log.warn("ActivityFeedConsumer received event without eventType from [{}], skipped: eventId={}",
                        sourceHint, eventId);
                return;
            }

            // 重复 eventId 短路：同一事件多次到达仅记录一条动态（库级兼底见 uk_source_event）。
            if (eventId != null && seenEventIds.putIfAbsent(eventId, Boolean.TRUE) != null) {
                log.info("ActivityFeedConsumer skipped duplicate eventId [{}] from [{}], eventType={}",
                        eventId, sourceHint, eventType);
                return;
            }

            switch (eventType.toLowerCase()) {
                case "enrollmentcreated", "paymentsuccess", "orderpaid" -> mapEnrollment(root, eventId, occurredAt);
                case "assignmentgraded", "assignment.graded" -> mapAssignmentGraded(root, eventId, occurredAt);
                case "assignmentsubmitted", "assignment.submitted" -> mapAssignmentSubmitted(root, eventId, occurredAt);
                case "coursecompleted" -> mapCourseCompleted(root, eventId, occurredAt);
                case "certificateissued" -> mapCertificateIssued(root, eventId, occurredAt);
                case "coursereviewed", "course.reviewed" -> mapCourseReviewed(root, eventId, occurredAt);
                case "coursepublished" -> mapCourseLifecycle(root, eventId, occurredAt, "COURSE_PUBLISHED");
                case "coursecreated" -> mapCourseLifecycle(root, eventId, occurredAt, "COURSE_CREATED");
                case "courseupdated" -> mapCourseLifecycle(root, eventId, occurredAt, "COURSE_UPDATED");
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

    /** 完课 → 学生完课动态（规格 §4.1 COURSE_COMPLETED）。 */
    private void mapCourseCompleted(JsonNode root, String eventId, LocalDateTime occurredAt) {
        String studentId = text(root, "studentId", "userId");
        if (studentId == null || studentId.isBlank()) {
            log.warn("CourseCompleted event without studentId skipped: eventId={}", eventId);
            return;
        }
        activityFeedService.recordActivity(
                studentId, ROLE_STUDENT, "COURSE_COMPLETED", "COURSE",
                text(root, "courseId"), text(root, "courseTitle", "title"),
                null, suffix(eventId, "COURSE_COMPLETED"), occurredAt);
    }

    /** 证书颁发 → 学生证书动态（规格 §4.1 CERTIFICATE_ISSUED）。 */
    private void mapCertificateIssued(JsonNode root, String eventId, LocalDateTime occurredAt) {
        String studentId = text(root, "studentId", "userId");
        if (studentId == null || studentId.isBlank()) {
            log.warn("CertificateIssued event without studentId skipped: eventId={}", eventId);
            return;
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        putIfPresent(extra, "certificateNo", root, "certificateNo");
        activityFeedService.recordActivity(
                studentId, ROLE_STUDENT, "CERTIFICATE_ISSUED", "COURSE",
                text(root, "courseId"), text(root, "courseTitle", "title"),
                extra.isEmpty() ? null : extra, suffix(eventId, "CERTIFICATE_ISSUED"), occurredAt);
    }

    /** 作业提交 → 学生交作业动态（规格 §4.1 ASSIGNMENT_SUBMITTED）。 */
    private void mapAssignmentSubmitted(JsonNode root, String eventId, LocalDateTime occurredAt) {
        String studentId = text(root, "studentId", "userId");
        if (studentId == null || studentId.isBlank()) {
            log.warn("AssignmentSubmitted event without studentId/userId skipped: eventId={}", eventId);
            return;
        }
        activityFeedService.recordActivity(
                studentId, ROLE_STUDENT, "ASSIGNMENT_SUBMITTED", "ASSIGNMENT",
                text(root, "assignmentId"), text(root, "assignmentTitle", "title"),
                null, suffix(eventId, "ASSIGNMENT_SUBMITTED"), occurredAt);
    }

    /** 课程评价 → 学生评价动态 + 教师侧学员评价动态（规格 §4.1）。 */
    private void mapCourseReviewed(JsonNode root, String eventId, LocalDateTime occurredAt) {
        String studentId = text(root, "studentId", "userId");
        String courseId = text(root, "courseId");
        String courseTitle = text(root, "courseTitle", "title");
        String teacherId = text(root, "teacherId");

        if (studentId != null && !studentId.isBlank()) {
            Map<String, Object> extra = new LinkedHashMap<>();
            putIfPresent(extra, "rating", root, "rating");
            activityFeedService.recordActivity(
                    studentId, ROLE_STUDENT, "COURSE_REVIEWED", "COURSE", courseId, courseTitle,
                    extra.isEmpty() ? null : extra, suffix(eventId, "COURSE_REVIEWED"), occurredAt);
        } else {
            log.warn("CourseReviewed event without studentId skipped: eventId={}", eventId);
        }

        if (teacherId != null && !teacherId.isBlank()) {
            Map<String, Object> teacherExtra = new LinkedHashMap<>();
            teacherExtra.put("studentId", studentId);
            putIfPresent(teacherExtra, "rating", root, "rating");
            activityFeedService.recordActivity(
                    teacherId, ROLE_TEACHER, "STUDENT_REVIEWED", "COURSE", courseId, courseTitle,
                    teacherExtra, suffix(eventId, "STUDENT_REVIEWED"), occurredAt);
        } else {
            log.warn("CourseReviewed event without teacherId, teacher-side activity skipped: eventId={}, courseId={}",
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
