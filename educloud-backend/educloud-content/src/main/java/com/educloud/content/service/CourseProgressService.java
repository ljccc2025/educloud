package com.educloud.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.common.id.IdentifierGenerator;
import com.educloud.content.dto.request.ProgressReportRequest;
import com.educloud.content.dto.response.CourseProgressResponse;
import com.educloud.content.entity.CourseCertificateEntity;
import com.educloud.content.entity.CourseContentEntity;
import com.educloud.content.entity.CoursewareEntity;
import com.educloud.content.entity.UserCourseProgressEntity;
import com.educloud.content.entity.UserCoursewareProgressEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.CourseContentMapper;
import com.educloud.content.mapper.CoursewareMapper;
import com.educloud.content.mapper.UserCourseProgressMapper;
import com.educloud.content.mapper.UserCoursewareProgressMapper;
import com.educloud.content.messaging.ContentEventPublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseProgressService {

    private static final Logger log = LoggerFactory.getLogger(CourseProgressService.class);
    private static final int MAX_HEARTBEAT_DELTA_SECONDS = 60;

    private final UserCoursewareProgressMapper coursewareProgressMapper;
    private final UserCourseProgressMapper courseProgressMapper;
    private final CoursewareMapper coursewareMapper;
    private final CourseContentMapper contentMapper;
    private final IdentifierGenerator idGenerator;
    private final CertificateService certificateService;
    private final ContentEventPublisher eventPublisher;
    private final CourseClient courseClient;

    @Transactional
    public CourseProgressResponse reportProgress(Long coursewareId, ProgressReportRequest req, Long studentId) {
        CoursewareEntity cw = coursewareMapper.selectById(coursewareId);
        if (cw == null || "DELETED".equals(cw.getStatus())) {
            throw new BusinessException(ContentErrorCode.COURSEWARE_NOT_FOUND, "Courseware not found");
        }

        // Anti-cheat verification
        if (req.getWatchedDeltaSeconds() > MAX_HEARTBEAT_DELTA_SECONDS) {
            throw new BusinessException(ContentErrorCode.INVALID_PROGRESS, "Watched delta seconds exceeds maximum allowable heartbeat interval");
        }
        if (cw.getDurationSeconds() > 0 && req.getPositionSeconds() > cw.getDurationSeconds() + 15) {
            throw new BusinessException(ContentErrorCode.INVALID_PROGRESS, "Position seconds exceeds courseware duration");
        }

        LocalDateTime now = LocalDateTime.now();
        Long courseId = cw.getCourseId();

        // 1. Update user_courseware_progress
        UserCoursewareProgressEntity cp = coursewareProgressMapper.selectOne(
                new LambdaQueryWrapper<UserCoursewareProgressEntity>()
                        .eq(UserCoursewareProgressEntity::getStudentId, studentId)
                        .eq(UserCoursewareProgressEntity::getCoursewareId, coursewareId));

        boolean isNewlyCompleted = false;
        if (cp == null) {
            cp = new UserCoursewareProgressEntity();
            cp.setId(idGenerator.nextId());
            cp.setStudentId(studentId);
            cp.setCourseId(courseId);
            cp.setCoursewareId(coursewareId);
            cp.setPositionSeconds(req.getPositionSeconds());
            cp.setWatchedSeconds(req.getWatchedDeltaSeconds());
            boolean completed = Boolean.TRUE.equals(req.getCompleted())
                    || (cw.getDurationSeconds() > 0 && cp.getWatchedSeconds() >= cw.getDurationSeconds() * 0.85);
            cp.setCompleted(completed);
            if (completed) {
                cp.setCompletedAt(now);
                isNewlyCompleted = true;
            }
            cp.setLastLearnedAt(now);
            coursewareProgressMapper.insert(cp);
        } else {
            cp.setPositionSeconds(req.getPositionSeconds());
            cp.setWatchedSeconds(cp.getWatchedSeconds() + req.getWatchedDeltaSeconds());
            if (!Boolean.TRUE.equals(cp.getCompleted())) {
                boolean completed = Boolean.TRUE.equals(req.getCompleted())
                        || (cw.getDurationSeconds() > 0 && cp.getWatchedSeconds() >= cw.getDurationSeconds() * 0.85);
                if (completed) {
                    cp.setCompleted(true);
                    cp.setCompletedAt(now);
                    isNewlyCompleted = true;
                }
            }
            cp.setLastLearnedAt(now);
            coursewareProgressMapper.updateById(cp);
        }

        // 2. Aggregate user_course_progress
        CourseContentEntity content = contentMapper.selectOne(
                new LambdaQueryWrapper<CourseContentEntity>().eq(CourseContentEntity::getCourseId, courseId));

        long totalCoursewares = 0;
        if (content != null && content.getPublishedRevisionId() != null) {
            totalCoursewares = coursewareMapper.selectCount(
                    new LambdaQueryWrapper<CoursewareEntity>()
                            .eq(CoursewareEntity::getContentRevisionId, content.getPublishedRevisionId())
                            .eq(CoursewareEntity::getStatus, "ACTIVE"));
        }
        if (totalCoursewares == 0) {
            totalCoursewares = 1;
        }

        long completedCoursewares = coursewareProgressMapper.selectCount(
                new LambdaQueryWrapper<UserCoursewareProgressEntity>()
                        .eq(UserCoursewareProgressEntity::getStudentId, studentId)
                        .eq(UserCoursewareProgressEntity::getCourseId, courseId)
                        .eq(UserCoursewareProgressEntity::getCompleted, true));

        int percent = (int) Math.min(100, (completedCoursewares * 100 / totalCoursewares));

        UserCourseProgressEntity ucp = courseProgressMapper.selectOne(
                new LambdaQueryWrapper<UserCourseProgressEntity>()
                        .eq(UserCourseProgressEntity::getStudentId, studentId)
                        .eq(UserCourseProgressEntity::getCourseId, courseId));

        if (ucp == null) {
            ucp = new UserCourseProgressEntity();
            ucp.setId(idGenerator.nextId());
            ucp.setStudentId(studentId);
            ucp.setCourseId(courseId);
            ucp.setCompletedCoursewareCount((int) completedCoursewares);
            ucp.setTotalCoursewareCount((int) totalCoursewares);
            ucp.setProgressPercent(percent);
            ucp.setLastLearnedCoursewareId(coursewareId);
            ucp.setUpdatedAt(now);
            courseProgressMapper.insert(ucp);
        } else {
            ucp.setCompletedCoursewareCount((int) completedCoursewares);
            ucp.setTotalCoursewareCount((int) totalCoursewares);
            ucp.setProgressPercent(percent);
            ucp.setLastLearnedCoursewareId(coursewareId);
            ucp.setUpdatedAt(now);
            courseProgressMapper.updateById(ucp);
        }

        // 3. 完课（进度 100%）触发证书颁发 + 完课/证书事件（角色化动态流阶段 3）；
        //    失败仅记日志，不阻断进度上报主流程。
        if (percent >= 100) {
            tryIssueCompletionCertificate(studentId, courseId, now);
        }

        CourseProgressResponse resp = new CourseProgressResponse();
        resp.setCourseId(courseId);
        resp.setCompletedCoursewareCount(ucp.getCompletedCoursewareCount());
        resp.setTotalCoursewareCount(ucp.getTotalCoursewareCount());
        resp.setProgressPercent(ucp.getProgressPercent());
        resp.setLastLearnedCoursewareId(ucp.getLastLearnedCoursewareId());
        resp.setUpdatedAt(ucp.getUpdatedAt());
        return resp;
    }

    /**
     * 完课触发：幂等颁发完课证书并发布 {@code CourseCompleted} + {@code CertificateIssued}
     * 事件（规格 §6.2）。幂等保障：已颁发直接跳过 + {@code uk_user_course} 唯一约束兜底。
     * 任何异常仅记日志不抛出——证书生成失败不得阻断进度上报主流程。
     */
    private void tryIssueCompletionCertificate(Long studentId, Long courseId, LocalDateTime completedAt) {
        try {
            if (certificateService.findCertificate(studentId, courseId) != null) {
                return; // 该课程已颁发证书，幂等跳过（不重复发事件）
            }

            // 课程标题快照 + 归属教师：尽力从 course 服务解析，失败降级不阻断。
            String courseTitle = null;
            Long teacherId = null;
            try {
                CourseClient.CourseSnapshot snapshot = courseClient.getCourseSnapshot(courseId);
                if (snapshot != null) {
                    courseTitle = snapshot.title();
                    teacherId = snapshot.ownerTeacherId();
                }
            } catch (Exception e) {
                log.warn("Failed to resolve course snapshot for certificate, fallback title used: "
                        + "courseId={}, {}", courseId, e.getMessage());
            }
            if (courseTitle == null || courseTitle.isBlank()) {
                courseTitle = "课程 " + courseId;
            }

            CourseCertificateEntity certificate =
                    certificateService.issueCertificate(studentId, courseId, courseTitle);
            eventPublisher.courseCompleted(courseId, studentId, 1L, completedAt);
            eventPublisher.certificateIssued(
                    certificate.getCertNo(), courseId, studentId, teacherId, 1L, certificate.getIssuedAt());
        } catch (Exception e) {
            log.warn("Completion certificate issuance failed, progress report unaffected: "
                    + "studentId={}, courseId={}", studentId, courseId, e);
        }
    }

    public CourseProgressResponse getCourseProgress(Long courseId, Long studentId) {
        UserCourseProgressEntity ucp = courseProgressMapper.selectOne(
                new LambdaQueryWrapper<UserCourseProgressEntity>()
                        .eq(UserCourseProgressEntity::getStudentId, studentId)
                        .eq(UserCourseProgressEntity::getCourseId, courseId));

        CourseProgressResponse resp = new CourseProgressResponse();
        resp.setCourseId(courseId);
        if (ucp != null) {
            resp.setCompletedCoursewareCount(ucp.getCompletedCoursewareCount());
            resp.setTotalCoursewareCount(ucp.getTotalCoursewareCount());
            resp.setProgressPercent(ucp.getProgressPercent());
            resp.setLastLearnedCoursewareId(ucp.getLastLearnedCoursewareId());
            resp.setUpdatedAt(ucp.getUpdatedAt());
        } else {
            resp.setCompletedCoursewareCount(0);
            resp.setTotalCoursewareCount(0);
            resp.setProgressPercent(0);
            resp.setLastLearnedCoursewareId(null);
            resp.setUpdatedAt(LocalDateTime.now());
        }
        return resp;
    }

    public List<CourseProgressResponse> batchGetCourseProgress(List<Long> courseIds, Long studentId) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserCourseProgressEntity> list = courseProgressMapper.selectList(
                new LambdaQueryWrapper<UserCourseProgressEntity>()
                        .eq(UserCourseProgressEntity::getStudentId, studentId)
                        .in(UserCourseProgressEntity::getCourseId, courseIds));

        Map<Long, UserCourseProgressEntity> map = list.stream()
                .collect(Collectors.toMap(UserCourseProgressEntity::getCourseId, p -> p));

        List<CourseProgressResponse> results = new ArrayList<>();
        for (Long courseId : courseIds) {
            UserCourseProgressEntity ucp = map.get(courseId);
            CourseProgressResponse resp = new CourseProgressResponse();
            resp.setCourseId(courseId);
            if (ucp != null) {
                resp.setCompletedCoursewareCount(ucp.getCompletedCoursewareCount());
                resp.setTotalCoursewareCount(ucp.getTotalCoursewareCount());
                resp.setProgressPercent(ucp.getProgressPercent());
                resp.setLastLearnedCoursewareId(ucp.getLastLearnedCoursewareId());
                resp.setUpdatedAt(ucp.getUpdatedAt());
            } else {
                resp.setCompletedCoursewareCount(0);
                resp.setTotalCoursewareCount(0);
                resp.setProgressPercent(0);
                resp.setLastLearnedCoursewareId(null);
                resp.setUpdatedAt(LocalDateTime.now());
            }
            results.add(resp);
        }
        return results;
    }
}
