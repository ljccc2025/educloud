package com.educloud.content.service;

import com.educloud.content.dto.request.AssignmentCreateRequest;
import com.educloud.content.dto.request.AssignmentSubmitRequest;
import com.educloud.content.dto.response.AssignmentResponse;
import com.educloud.content.messaging.ContentEventPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private static final String ASSIGNMENT_MAP_KEY = "educloud:assignments:map";
    private static final String ASSIGNMENT_ORDER_KEY = "educloud:assignments:order";
    private static final String SUBMISSION_PREFIX = "educloud:assignment_submissions:";
    private static final String SUBMISSIONS_BY_ASSIGNMENT_PREFIX = "educloud:submissions_by_assignment:";
    private static final String SUBMISSIONS_BY_ID_PREFIX = "educloud:submissions_by_id:";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ContentEventPublisher contentEventPublisher;
    private final CourseClient courseClient;

    public Map<String, String> resolveStudentProfile(Long studentId, String givenName, String givenAvatar) {
        String finalName = (givenName != null && !givenName.isBlank() && !"学员".equals(givenName.trim())) ? givenName.trim() : null;
        String finalAvatar = (givenAvatar != null && !givenAvatar.isBlank() && !givenAvatar.contains("dicebear")) ? givenAvatar.trim() : null;

        if (studentId != null && jdbcTemplate != null) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT u.username, p.display_name, p.avatar_file_id " +
                        "FROM educloud_user.sys_user u " +
                        "LEFT JOIN educloud_user.user_profile p ON u.id = p.user_id " +
                        "WHERE u.id = ?",
                        studentId
                );
                if (!rows.isEmpty()) {
                    Map<String, Object> r = rows.get(0);
                    String dbDisplayName = (String) r.get("display_name");
                    String dbUsername = (String) r.get("username");
                    Object dbAvatarFileId = r.get("avatar_file_id");

                    if (dbDisplayName != null && !dbDisplayName.isBlank() && !"学员".equals(dbDisplayName.trim())) {
                        finalName = dbDisplayName.trim();
                    } else if (dbUsername != null && !dbUsername.isBlank()) {
                        finalName = dbUsername.trim();
                    }
                    if (dbAvatarFileId != null && !String.valueOf(dbAvatarFileId).isBlank()) {
                        try {
                            List<Map<String, Object>> fileRows = jdbcTemplate.queryForList(
                                    "SELECT bucket, object_key FROM educloud_file.file_object WHERE id = ?",
                                    dbAvatarFileId
                            );
                            if (!fileRows.isEmpty()) {
                                String bucket = (String) fileRows.get(0).get("bucket");
                                String objectKey = (String) fileRows.get(0).get("object_key");
                                if (bucket != null && objectKey != null) {
                                    finalAvatar = "http://192.168.100.136:9000/" + bucket + "/" + objectKey;
                                }
                            }
                        } catch (Exception fe) {
                            log.warn("Failed to query file_object for avatarId {}: {}", dbAvatarFileId, fe.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to resolve user profile for studentId {}: {}", studentId, e.getMessage());
            }
        }

        if (finalName == null || finalName.isBlank() || "学员".equals(finalName)) {
            finalName = (studentId != null) ? "学员 " + studentId : "学员";
        }
        if (finalAvatar == null || finalAvatar.isBlank()) {
            finalAvatar = "https://api.dicebear.com/7.x/initials/svg?seed=" + finalName + "&backgroundColor=1e1b4b,d97706,4f46e5&textColor=ffffff&fontWeight=500&fontSize=24";
        }
        return Map.of("name", finalName, "avatar", finalAvatar);
    }

    @PostConstruct
    public void init() {
        try {
            Long count = redisTemplate.opsForHash().size(ASSIGNMENT_MAP_KEY);
            if (count == null || count == 0) {
                seedDefaultAssignments();
            }
        } catch (Exception e) {
            log.warn("Failed to check or seed default assignments in Redis: {}", e.getMessage());
        }
    }

    private void seedDefaultAssignments() {
        LocalDateTime now = LocalDateTime.now();
        List<AssignmentResponse> seeds = List.of(
                AssignmentResponse.builder()
                        .id("asg-001")
                        .courseId("c_1001")
                        .courseTitle("Spring Boot 微服务实践")
                        .courseName("Spring Boot 微服务实践")
                        .title("第三章作业：分布式事务与 Seata 框架集成")
                        .description("完成微服务订单与库存服务的分布式事务保障，撰写集成步骤并提供关键代码截图与配置文件。")
                        .dueDate(now.plusDays(5).format(DATE_FORMATTER))
                        .totalScore(100)
                        .status("PUBLISHED")
                        .submissionCount(0)
                        .gradedCount(0)
                        .publishedAt(now.minusDays(1).format(DATE_TIME_FORMATTER))
                        .build(),
                AssignmentResponse.builder()
                        .id("asg-002")
                        .courseId("c_1002")
                        .courseTitle("Python 自动化测试实战")
                        .courseName("Python 自动化测试实战")
                        .title("Pytest 夹具与自动化测试用例编写")
                        .description("使用 Pytest 编写针对 RESTful 接口的完整自动化测试套件，包含参数化与生成 Allure 测试报告。")
                        .dueDate(now.plusDays(8).format(DATE_FORMATTER))
                        .totalScore(100)
                        .status("PUBLISHED")
                        .submissionCount(0)
                        .gradedCount(0)
                        .publishedAt(now.minusDays(2).format(DATE_TIME_FORMATTER))
                        .build(),
                AssignmentResponse.builder()
                        .id("asg-003")
                        .courseId("c_1003")
                        .courseTitle("前端工程化与 React 进阶")
                        .courseName("前端工程化与 React 进阶")
                        .title("实现自定义 Hook 与状态管理架构")
                        .description("编写 useDebounce、useLocalStorage 及轻量级响应式全局状态管理器，提供单元测试。")
                        .dueDate(now.minusDays(1).format(DATE_FORMATTER))
                        .totalScore(100)
                        .status("PUBLISHED")
                        .submissionCount(1)
                        .gradedCount(0)
                        .publishedAt(now.minusDays(3).format(DATE_TIME_FORMATTER))
                        .build(),
                AssignmentResponse.builder()
                        .id("asg-004")
                        .courseId("c_1004")
                        .courseTitle("Kubernetes 云原生架构")
                        .courseName("Kubernetes 云原生架构")
                        .title("Helm Chart 模板化部署与 Ingress 配置")
                        .description("将微服务集群打包为标准 Helm Chart，配置 Ingress-Nginx 与 TLS 证书自动签发。")
                        .dueDate(now.minusDays(7).format(DATE_FORMATTER))
                        .totalScore(100)
                        .status("PUBLISHED")
                        .submissionCount(1)
                        .gradedCount(1)
                        .publishedAt(now.minusDays(8).format(DATE_TIME_FORMATTER))
                        .build(),
                AssignmentResponse.builder()
                        .id("asg-005")
                        .courseId("c_1005")
                        .courseTitle("深入浅出数据结构与算法")
                        .courseName("深入浅出数据结构与算法")
                        .title("红黑树与 B+ 树底层实现与性能评测")
                        .description("手写红黑树左旋、右旋与变色插入逻辑，对比在百万级数据下的查询吞吐性能。")
                        .dueDate(now.plusDays(12).format(DATE_FORMATTER))
                        .totalScore(100)
                        .status("PUBLISHED")
                        .submissionCount(0)
                        .gradedCount(0)
                        .publishedAt(now.minusDays(1).format(DATE_TIME_FORMATTER))
                        .build()
        );

        for (AssignmentResponse seed : seeds) {
            saveAssignmentDirect(seed);
        }

        // Seed submissions for asg-003 and asg-004
        seedSubmission("asg-003", 101L, "李明", "已完成自定义 Hook 与响应式状态管理器实现，包含单元测试用例及覆盖率报告。", "SUBMITTED", null, null, now.minusDays(1).format(DATE_TIME_FORMATTER));
        seedSubmission("asg-004", 102L, "张华", "已将微服务各模块打包为标准 Helm Chart，并配置 Ingress-Nginx 转发规则与 HTTPS 自动证书。", "GRADED", 95, "Chart 模板结构非常清晰，Values 默认值配置合理，优秀！", now.minusDays(7).format(DATE_TIME_FORMATTER));
    }

    private void seedSubmission(String assignmentId, Long studentId, String studentName, String content, String status, Integer score, String feedback, String time) {
        String subId = "sub-" + studentId + "-" + assignmentId;
        Map<String, Object> subMap = new HashMap<>();
        subMap.put("id", subId);
        subMap.put("assignmentId", assignmentId);
        subMap.put("studentId", String.valueOf(studentId));
        subMap.put("studentName", studentName);
        subMap.put("studentAvatar", "https://api.dicebear.com/7.x/bottts/svg?seed=" + studentId);
        subMap.put("content", content);
        subMap.put("files", Collections.emptyList());
        subMap.put("note", "");
        subMap.put("submittedAt", time);
        subMap.put("status", status);
        if (score != null) subMap.put("score", score);
        if (feedback != null) subMap.put("feedback", feedback);

        try {
            String json = objectMapper.writeValueAsString(subMap);
            redisTemplate.opsForHash().put(SUBMISSIONS_BY_ASSIGNMENT_PREFIX + assignmentId, String.valueOf(studentId), json);
            redisTemplate.opsForValue().set(SUBMISSIONS_BY_ID_PREFIX + subId, json);
            redisTemplate.opsForValue().set(SUBMISSION_PREFIX + studentId + ":" + assignmentId, json);
        } catch (Exception e) {
            log.error("Failed to seed submission", e);
        }
    }

    private void saveAssignmentDirect(AssignmentResponse assignment) {
        try {
            String json = objectMapper.writeValueAsString(assignment);
            redisTemplate.opsForHash().put(ASSIGNMENT_MAP_KEY, assignment.getId(), json);
            redisTemplate.opsForList().rightPush(ASSIGNMENT_ORDER_KEY, assignment.getId());
        } catch (Exception e) {
            log.error("Failed to save assignment {}", assignment.getId(), e);
        }
    }

    public List<AssignmentResponse> getAllAssignments() {
        try {
            List<String> ids = redisTemplate.opsForList().range(ASSIGNMENT_ORDER_KEY, 0, -1);
            if (ids == null || ids.isEmpty()) {
                seedDefaultAssignments();
                ids = redisTemplate.opsForList().range(ASSIGNMENT_ORDER_KEY, 0, -1);
            }
            List<AssignmentResponse> list = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            if (ids != null) {
                for (String id : ids) {
                    if (seen.add(id)) {
                        AssignmentResponse res = getAssignmentById(id);
                        if (res != null) {
                            list.add(res);
                        }
                    }
                }
            }
            return list;
        } catch (Exception e) {
            log.error("Error reading all assignments from Redis", e);
            return Collections.emptyList();
        }
    }

    public AssignmentResponse getAssignmentById(String id) {
        try {
            Object obj = redisTemplate.opsForHash().get(ASSIGNMENT_MAP_KEY, id);
            if (obj != null) {
                AssignmentResponse res = objectMapper.readValue(obj.toString(), AssignmentResponse.class);
                // Attach submissions list
                List<Map<String, Object>> submissions = getSubmissionsForAssignment(id);
                res.setSubmissions(submissions);
                res.setSubmissionCount(submissions.size());
                res.setGradedCount((int) submissions.stream().filter(s -> "GRADED".equals(s.get("status"))).count());
                return res;
            }
        } catch (Exception e) {
            log.error("Error getting assignment {}", id, e);
        }
        return null;
    }

    public List<Map<String, Object>> getSubmissionsForAssignment(String assignmentId) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(SUBMISSIONS_BY_ASSIGNMENT_PREFIX + assignmentId);
            if (entries != null) {
                for (Object v : entries.values()) {
                    if (v != null) {
                        Map<String, Object> subMap = objectMapper.readValue(v.toString(), new TypeReference<>() {});
                        Object sIdObj = subMap.get("studentId");
                        if (sIdObj != null) {
                            try {
                                Long sId = Long.parseLong(String.valueOf(sIdObj));
                                String existingName = (String) subMap.get("studentName");
                                String existingAvatar = (String) subMap.get("studentAvatar");
                                Map<String, String> resolved = resolveStudentProfile(sId, existingName, existingAvatar);
                                subMap.put("studentName", resolved.get("name"));
                                subMap.put("studentAvatar", resolved.get("avatar"));
                            } catch (Exception ignored) {
                            }
                        }
                        list.add(subMap);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to read submissions for assignment {}", assignmentId, e);
        }
        return list;
    }

    public AssignmentResponse createAssignment(AssignmentCreateRequest req, Long teacherId) {
        String id = "a-" + System.currentTimeMillis() + (int) (Math.random() * 1000);
        String courseName = req.getCourseName() != null ? req.getCourseName() : (req.getCourseTitle() != null ? req.getCourseTitle() : "课程");
        String nowStr = LocalDateTime.now().format(DATE_TIME_FORMATTER);

        AssignmentResponse assignment = AssignmentResponse.builder()
                .id(id)
                .courseId(req.getCourseId())
                .courseTitle(courseName)
                .courseName(courseName)
                .title(req.getTitle().trim())
                .description(req.getDescription() != null ? req.getDescription().trim() : "")
                .dueDate(req.getDueDate() != null ? req.getDueDate() : "")
                .totalScore(req.getTotalScore() != null ? req.getTotalScore() : 100)
                .status("PUBLISHED")
                .allowLateSubmission(Boolean.TRUE.equals(req.getAllowLateSubmission()))
                .maxAttempts(req.getMaxAttempts() != null ? req.getMaxAttempts() : 1)
                .submissionCount(0)
                .gradedCount(0)
                .publishedAt(nowStr)
                .submissions(Collections.emptyList())
                .build();

        try {
            String json = objectMapper.writeValueAsString(assignment);
            redisTemplate.opsForHash().put(ASSIGNMENT_MAP_KEY, id, json);
            redisTemplate.opsForList().leftPush(ASSIGNMENT_ORDER_KEY, id);
        } catch (Exception e) {
            log.error("Failed to save new assignment", e);
            throw new RuntimeException("发布作业失败: " + e.getMessage());
        }

        return assignment;
    }

    public AssignmentResponse publishAssignment(String id) {
        AssignmentResponse existing = getAssignmentById(id);
        if (existing == null) {
            throw new IllegalArgumentException("作业不存在: " + id);
        }
        existing.setStatus("PUBLISHED");
        existing.setPublishedAt(LocalDateTime.now().format(DATE_TIME_FORMATTER));
        try {
            String json = objectMapper.writeValueAsString(existing);
            redisTemplate.opsForHash().put(ASSIGNMENT_MAP_KEY, id, json);
        } catch (Exception e) {
            log.error("Failed to update assignment status", e);
        }
        return existing;
    }

    /** 作业文档中的 courseId 为字符串；非数字视为不可见，避免整表读取被打断。 */
    private static Long parseCourseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Assignment has non-numeric courseId={}, hidden from student view", raw);
            return null;
        }
    }

    public List<AssignmentResponse> getStudentAssignments(Long studentId) {
        List<AssignmentResponse> all = getAllAssignments();
        List<AssignmentResponse> studentView = new ArrayList<>();
        // 仅返回已报名课程的作业，与考试列表口径保持一致（按课程去重，避免逐作业远程调用）
        Map<Long, Boolean> enrolledByCourse = new LinkedHashMap<>();

        for (AssignmentResponse a : all) {
            if (!"PUBLISHED".equals(a.getStatus())) {
                continue;
            }
            Long courseId = parseCourseId(a.getCourseId());
            if (courseId == null
                    || !enrolledByCourse.computeIfAbsent(courseId, cid -> courseClient.isEnrolled(cid, studentId))) {
                continue;
            }
            AssignmentResponse copy = copyAssignment(a);
            // Check student submission
            String subKey = SUBMISSION_PREFIX + studentId + ":" + a.getId();
            String subJson = redisTemplate.opsForValue().get(subKey);
            if (subJson != null) {
                try {
                    Map<String, Object> subMap = objectMapper.readValue(subJson, new TypeReference<>() {});
                    copy.setStatus((String) subMap.getOrDefault("status", "SUBMITTED"));
                    copy.setSubmitDate((String) subMap.get("submittedAt"));
                    if (subMap.get("score") != null) {
                        copy.setScore(((Number) subMap.get("score")).intValue());
                    }
                    if (subMap.get("feedback") != null) {
                        copy.setFeedback((String) subMap.get("feedback"));
                    }
                    copy.setSubmission(subMap);
                } catch (Exception e) {
                    log.warn("Failed to parse student submission", e);
                }
            } else {
                copy.setStatus("PENDING");
            }
            studentView.add(copy);
        }

        return studentView;
    }

    public AssignmentResponse submitAssignment(String assignmentId, AssignmentSubmitRequest req, Long studentId, String studentName) {
        AssignmentResponse assignment = getAssignmentById(assignmentId);
        if (assignment == null) {
            throw new IllegalArgumentException("作业不存在: " + assignmentId);
        }

        String now = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        String subId = "sub-" + studentId + "-" + assignmentId;
        Map<String, String> profile = resolveStudentProfile(studentId, (studentName != null && !studentName.isBlank()) ? studentName : req.getStudentName(), req.getStudentAvatar());
        String actualName = profile.get("name");
        String avatar = profile.get("avatar");

        Map<String, Object> subMap = new HashMap<>();
        subMap.put("id", subId);
        subMap.put("assignmentId", assignmentId);
        subMap.put("studentId", String.valueOf(studentId));
        subMap.put("studentName", actualName);
        subMap.put("studentAvatar", avatar);
        subMap.put("content", req.getContent());
        subMap.put("files", req.getFiles() != null ? req.getFiles() : Collections.emptyList());
        subMap.put("note", req.getNote() != null ? req.getNote() : "");
        subMap.put("submittedAt", now);
        subMap.put("status", "SUBMITTED");

        String subKey = SUBMISSION_PREFIX + studentId + ":" + assignmentId;
        try {
            String json = objectMapper.writeValueAsString(subMap);
            redisTemplate.opsForValue().set(subKey, json);
            redisTemplate.opsForHash().put(SUBMISSIONS_BY_ASSIGNMENT_PREFIX + assignmentId, String.valueOf(studentId), json);
            redisTemplate.opsForValue().set(SUBMISSIONS_BY_ID_PREFIX + subId, json);

            // update submission count
            List<Map<String, Object>> subs = getSubmissionsForAssignment(assignmentId);
            assignment.setSubmissionCount(subs.size());
            assignment.setSubmissions(subs);
            redisTemplate.opsForHash().put(ASSIGNMENT_MAP_KEY, assignmentId, objectMapper.writeValueAsString(assignment));
        } catch (Exception e) {
            log.error("Failed to save submission", e);
            throw new RuntimeException("提交作业失败: " + e.getMessage());
        }

        // 动态流阶段 2：发布作业提交领域事件（Outbox 落库）；失败不阻断提交主流程。
        try {
            contentEventPublisher.assignmentSubmitted(
                    assignmentId, assignment.getTitle(), assignment.getCourseId(),
                    studentId, 1L, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Failed to publish AssignmentSubmitted event for assignment {} student {}",
                    assignmentId, studentId, e);
        }

        AssignmentResponse res = copyAssignment(assignment);
        res.setStatus("SUBMITTED");
        res.setSubmitDate(now);
        res.setSubmission(subMap);
        return res;
    }

    public void gradeSubmission(String submissionId, Long studentId, String assignmentId, Integer score, String feedback) {
        String subJson = null;
        if (submissionId != null) {
            subJson = redisTemplate.opsForValue().get(SUBMISSIONS_BY_ID_PREFIX + submissionId);
        }
        if (subJson == null && studentId != null && assignmentId != null) {
            subJson = redisTemplate.opsForValue().get(SUBMISSION_PREFIX + studentId + ":" + assignmentId);
        }

        if (subJson != null) {
            try {
                Map<String, Object> subMap = objectMapper.readValue(subJson, new TypeReference<>() {});
                String actualAssignmentId = assignmentId != null ? assignmentId : (String) subMap.get("assignmentId");
                String actualStudentIdStr = (String) subMap.get("studentId");
                Long actualStudentId = actualStudentIdStr != null ? Long.valueOf(actualStudentIdStr) : studentId;

                subMap.put("score", score);
                subMap.put("feedback", feedback);
                subMap.put("status", "GRADED");
                subMap.put("gradedAt", LocalDateTime.now().format(DATE_TIME_FORMATTER));

                String updatedJson = objectMapper.writeValueAsString(subMap);
                if (submissionId != null) {
                    redisTemplate.opsForValue().set(SUBMISSIONS_BY_ID_PREFIX + submissionId, updatedJson);
                }
                if (actualStudentId != null && actualAssignmentId != null) {
                    redisTemplate.opsForValue().set(SUBMISSION_PREFIX + actualStudentId + ":" + actualAssignmentId, updatedJson);
                    redisTemplate.opsForHash().put(SUBMISSIONS_BY_ASSIGNMENT_PREFIX + actualAssignmentId, String.valueOf(actualStudentId), updatedJson);
                }

                AssignmentResponse assignment = getAssignmentById(actualAssignmentId);
                if (assignment != null) {
                    List<Map<String, Object>> subs = getSubmissionsForAssignment(actualAssignmentId);
                    assignment.setSubmissions(subs);
                    assignment.setGradedCount((int) subs.stream().filter(s -> "GRADED".equals(s.get("status"))).count());
                    redisTemplate.opsForHash().put(ASSIGNMENT_MAP_KEY, actualAssignmentId, objectMapper.writeValueAsString(assignment));
                }

                // 动态流阶段 2：发布作业批改领域事件（Outbox 落库，路由 assignment.graded）；
                // 失败不阻断批改主流程。
                try {
                    contentEventPublisher.assignmentGraded(
                            actualAssignmentId,
                            assignment != null ? assignment.getTitle() : null,
                            assignment != null ? assignment.getCourseId() : null,
                            actualStudentId, score, feedback, 1L, LocalDateTime.now());
                } catch (Exception e) {
                    log.warn("Failed to publish AssignmentGraded event for assignment {} student {}",
                            actualAssignmentId, actualStudentId, e);
                }
            } catch (Exception e) {
                log.error("Failed to grade submission", e);
            }
        }
    }

    private AssignmentResponse copyAssignment(AssignmentResponse src) {
        return AssignmentResponse.builder()
                .id(src.getId())
                .courseId(src.getCourseId())
                .courseTitle(src.getCourseTitle())
                .courseName(src.getCourseName())
                .title(src.getTitle())
                .description(src.getDescription())
                .dueDate(src.getDueDate())
                .totalScore(src.getTotalScore())
                .status(src.getStatus())
                .allowLateSubmission(src.getAllowLateSubmission())
                .maxAttempts(src.getMaxAttempts())
                .submissionCount(src.getSubmissionCount())
                .gradedCount(src.getGradedCount())
                .publishedAt(src.getPublishedAt())
                .score(src.getScore())
                .submitDate(src.getSubmitDate())
                .feedback(src.getFeedback())
                .submission(src.getSubmission())
                .submissions(src.getSubmissions())
                .build();
    }
}
