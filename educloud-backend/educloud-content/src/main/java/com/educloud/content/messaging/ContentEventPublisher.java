package com.educloud.content.messaging;

import com.educloud.common.web.RequestContextAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ContentEventPublisher {

    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final RequestContextAccessor requestContextAccessor;

    public void contentRevisionPublished(
            Long courseId,
            Long contentRootId,
            Long publishedRevisionId,
            Integer revisionNo,
            long aggregateVersion,
            LocalDateTime publishedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", courseId);
        payload.put("contentRootId", contentRootId);
        payload.put("publishedRevisionId", publishedRevisionId);
        payload.put("revisionNo", revisionNo);
        payload.put("aggregateVersion", aggregateVersion);
        payload.put("publishedAt", publishedAt.toString());

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize ContentRevisionPublished payload", e);
        }

        outboxWriter.write(
                "CourseContent",
                String.valueOf(contentRootId),
                "ContentRevisionPublished",
                1,
                aggregateVersion,
                payloadJson,
                requestContextAccessor.requestId(),
                requestContextAccessor.traceId().orElse(null));
    }

    /**
     * 作业提交事件（角色化动态流阶段 2）：学生提交作业后发布，
     * 经 Outbox 投递到内容域交换机（routing key assignment.submitted）。
     */
    public void assignmentSubmitted(
            String assignmentId,
            String assignmentTitle,
            String courseId,
            Long studentId,
            long aggregateVersion,
            LocalDateTime submittedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assignmentId", assignmentId);
        payload.put("assignmentTitle", assignmentTitle);
        payload.put("courseId", courseId);
        payload.put("studentId", studentId);
        payload.put("aggregateVersion", aggregateVersion);
        payload.put("submittedAt", submittedAt.toString());
        writeOutbox("Assignment", assignmentId, "AssignmentSubmitted", aggregateVersion, payload);
    }

    /**
     * 作业批改事件（角色化动态流阶段 2）：教师批改后发布，含 score/feedback，
     * 经 Outbox 投递到全域总线 educloud.events（routing key assignment.graded，
     * analytics 动态流作业队列与 notification 均按该路由键定向订阅）。
     */
    public void assignmentGraded(
            String assignmentId,
            String assignmentTitle,
            String courseId,
            Long studentId,
            Integer score,
            String feedback,
            long aggregateVersion,
            LocalDateTime gradedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assignmentId", assignmentId);
        payload.put("assignmentTitle", assignmentTitle);
        payload.put("courseId", courseId);
        payload.put("studentId", studentId);
        payload.put("score", score);
        payload.put("feedback", feedback);
        payload.put("aggregateVersion", aggregateVersion);
        payload.put("gradedAt", gradedAt.toString());
        writeOutbox("Assignment", assignmentId, "AssignmentGraded", aggregateVersion, payload);
    }

    /**
     * 完课事件（角色化动态流阶段 3 使用）：学生完成课程学习时发布。
     * courseTitle 为课程标题快照，供动态流展示（规格 §4.1 文案模板）。
     */
    public void courseCompleted(
            Long courseId,
            Long studentId,
            String courseTitle,
            long aggregateVersion,
            LocalDateTime completedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", courseId);
        payload.put("studentId", studentId);
        payload.put("courseTitle", courseTitle);
        payload.put("aggregateVersion", aggregateVersion);
        payload.put("completedAt", completedAt.toString());
        writeOutbox("LearningProgress", String.valueOf(courseId), "CourseCompleted", aggregateVersion, payload);
    }

    /**
     * 证书颁发事件（角色化动态流阶段 3 使用）：完课证书生成后发布。
     * courseTitle 为课程标题快照，供证书动态展示（规格 §4.1 文案模板）。
     */
    public void certificateIssued(
            String certificateNo,
            Long courseId,
            Long studentId,
            Long teacherId,
            String courseTitle,
            long aggregateVersion,
            LocalDateTime issuedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("certificateNo", certificateNo);
        payload.put("courseId", courseId);
        payload.put("studentId", studentId);
        payload.put("teacherId", teacherId);
        payload.put("courseTitle", courseTitle);
        payload.put("aggregateVersion", aggregateVersion);
        payload.put("issuedAt", issuedAt.toString());
        writeOutbox("Certificate", certificateNo, "CertificateIssued", aggregateVersion, payload);
    }

    /**
     * 考试判分完成事件（在线考试模块）：交卷/超时收敛判分后发布，
     * 经 Outbox 投递到全域总线 educloud.events（routing key exam.graded，
     * analytics 动态流考试队列与 notification 均按该路由键定向订阅）。
     */
    public void examGraded(
            Long examId,
            String examTitle,
            Long courseId,
            String courseTitle,
            Long studentId,
            Integer score,
            boolean passed,
            long aggregateVersion,
            LocalDateTime gradedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("examId", examId);
        payload.put("examTitle", examTitle);
        payload.put("courseId", courseId);
        payload.put("courseTitle", courseTitle);
        payload.put("studentId", studentId);
        payload.put("score", score);
        payload.put("passed", passed);
        payload.put("aggregateVersion", aggregateVersion);
        payload.put("gradedAt", gradedAt.toString());
        writeOutbox("Exam", String.valueOf(examId), "ExamGraded", aggregateVersion, payload);
    }

    private void writeOutbox(String aggregateType, String aggregateId, String eventType,
                             long aggregateVersion, Map<String, Object> payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize " + eventType + " payload", e);
        }
        outboxWriter.write(
                aggregateType,
                aggregateId,
                eventType,
                1,
                aggregateVersion,
                payloadJson,
                requestContextAccessor.requestId(),
                requestContextAccessor.traceId().orElse(null));
    }
}
