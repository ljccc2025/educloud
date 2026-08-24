package com.educloud.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.common.id.IdentifierGenerator;
import com.educloud.content.dto.request.ProgressReportRequest;
import com.educloud.content.dto.response.CourseProgressResponse;
import com.educloud.content.entity.CourseContentEntity;
import com.educloud.content.entity.CoursewareEntity;
import com.educloud.content.entity.UserCourseProgressEntity;
import com.educloud.content.entity.UserCoursewareProgressEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.CourseContentMapper;
import com.educloud.content.mapper.CoursewareMapper;
import com.educloud.content.mapper.UserCourseProgressMapper;
import com.educloud.content.mapper.UserCoursewareProgressMapper;
import lombok.RequiredArgsConstructor;
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

    private static final int MAX_HEARTBEAT_DELTA_SECONDS = 60;

    private final UserCoursewareProgressMapper coursewareProgressMapper;
    private final UserCourseProgressMapper courseProgressMapper;
    private final CoursewareMapper coursewareMapper;
    private final CourseContentMapper contentMapper;
    private final IdentifierGenerator idGenerator;

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

        CourseProgressResponse resp = new CourseProgressResponse();
        resp.setCourseId(courseId);
        resp.setCompletedCoursewareCount(ucp.getCompletedCoursewareCount());
        resp.setTotalCoursewareCount(ucp.getTotalCoursewareCount());
        resp.setProgressPercent(ucp.getProgressPercent());
        resp.setLastLearnedCoursewareId(ucp.getLastLearnedCoursewareId());
        resp.setUpdatedAt(ucp.getUpdatedAt());
        return resp;
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
