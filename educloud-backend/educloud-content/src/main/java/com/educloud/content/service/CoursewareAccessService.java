package com.educloud.content.service;

import com.educloud.common.error.BusinessException;
import com.educloud.content.dto.response.CoursewareDownloadUrlResponse;
import com.educloud.content.entity.CoursewareEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.CoursewareMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CoursewareAccessService {

    private final CoursewareMapper coursewareMapper;
    private final FileClient fileClient;

    public CoursewareDownloadUrlResponse getDownloadUrl(
            Long coursewareId, Long userId, Set<String> roles, Set<String> permissions) {
        CoursewareEntity cw = coursewareMapper.selectById(coursewareId);
        if (cw == null || "DELETED".equals(cw.getStatus())) {
            throw new BusinessException(ContentErrorCode.COURSEWARE_NOT_FOUND, "Courseware not found");
        }

        boolean isStaff = roles.contains("TEACHER") || roles.contains("SYSTEM_ADMIN") || roles.contains("SUPER_ADMIN")
                || permissions.contains("content:manage") || permissions.contains("content:audit");
        boolean isFree = Boolean.TRUE.equals(cw.getFreePreview());
        boolean isStudent = userId != null;

        if (!isStaff && !isFree && !isStudent) {
            throw new BusinessException(ContentErrorCode.COURSEWARE_ACCESS_DENIED, "Enrollment or login required to access this courseware");
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
}
