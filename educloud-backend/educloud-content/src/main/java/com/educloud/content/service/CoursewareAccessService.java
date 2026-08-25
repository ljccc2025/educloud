package com.educloud.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.content.dto.response.CoursewareDownloadUrlResponse;
import com.educloud.content.entity.CourseContentEntity;
import com.educloud.content.entity.CoursewareEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.CourseContentMapper;
import com.educloud.content.mapper.CoursewareMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CoursewareAccessService {

    private final CoursewareMapper coursewareMapper;
    private final CourseContentMapper courseContentMapper;
    private final FileClient fileClient;
    private final CourseClient courseClient;

    public CoursewareDownloadUrlResponse getDownloadUrl(
            Long coursewareId, Long userId, Set<String> roles, Set<String> permissions) {
        CoursewareEntity cw = coursewareMapper.selectById(coursewareId);
        if (cw == null || "DELETED".equals(cw.getStatus())) {
            throw new BusinessException(ContentErrorCode.COURSEWARE_NOT_FOUND, "Courseware not found");
        }

        boolean isStaff = roles.contains("TEACHER") || roles.contains("SYSTEM_ADMIN") || roles.contains("SUPER_ADMIN")
                || permissions.contains("content:manage") || permissions.contains("content:audit");
        boolean isFree = Boolean.TRUE.equals(cw.getFreePreview());

        if (!isStaff) {
            // BUG-003 修复：非 staff 只能访问课程内容根当前已发布版本的课件，
            // 草稿/审核中/驳回版本的课件对外不可下载（staff 可预览任意版本）。
            requirePublishedRevision(cw);
            // BUG-002 修复：非 staff 且非免费预览时，必须有有效报名（ACTIVE
            // enrollment）——免费课程走 enroll、付费课程由订单支付开课，权益
            // 权威来源均为 course 服务；匿名用户仅允许免费预览内容。
            if (!isFree && !hasEnrollmentAccess(cw.getCourseId(), userId)) {
                throw new BusinessException(ContentErrorCode.COURSEWARE_ACCESS_DENIED,
                        userId == null
                                ? "Login and enrollment required to access this courseware"
                                : "Enrollment required to access this courseware");
            }
        }

        String downloadUrl = null;
        if (cw.getFileId() != null) {
            downloadUrl = fileClient.getDownloadUrl(cw.getFileId(), cw.getId(), userId);
        } else if (cw.getExternalUrl() != null && !cw.getExternalUrl().isBlank()) {
            downloadUrl = cw.getExternalUrl();
        }

        if (downloadUrl == null) {
            throw new BusinessException(ContentErrorCode.COURSEWARE_NOT_FOUND, "No media file or external URL associated with courseware");
        }

        CoursewareDownloadUrlResponse response = new CoursewareDownloadUrlResponse();
        response.setCoursewareId(coursewareId);
        response.setDownloadUrl(downloadUrl);
        response.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        return response;
    }

    /** 课件必须属于课程内容根当前 publishedRevisionId（BUG-003）。 */
    private void requirePublishedRevision(CoursewareEntity cw) {
        CourseContentEntity contentRoot = courseContentMapper.selectOne(
                new LambdaQueryWrapper<CourseContentEntity>().eq(CourseContentEntity::getCourseId, cw.getCourseId()));
        if (contentRoot == null
                || !Objects.equals(contentRoot.getPublishedRevisionId(), cw.getContentRevisionId())) {
            throw new BusinessException(ContentErrorCode.COURSEWARE_ACCESS_DENIED,
                    "Courseware belongs to an unpublished revision");
        }
    }

    /** 学员报名校验（BUG-002）：course 服务不可用时由 CourseClient fail-closed。 */
    private boolean hasEnrollmentAccess(Long courseId, Long userId) {
        return userId != null && courseClient.isEnrolled(courseId, userId);
    }
}
