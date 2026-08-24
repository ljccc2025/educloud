package com.educloud.content.service;

import com.educloud.common.error.BusinessException;
import com.educloud.common.id.IdentifierGenerator;
import com.educloud.content.dto.request.CoursewareCreateRequest;
import com.educloud.content.dto.request.CoursewareUpdateRequest;
import com.educloud.content.dto.response.CoursewareResponse;
import com.educloud.content.entity.ChapterEntity;
import com.educloud.content.entity.ContentRevisionEntity;
import com.educloud.content.entity.CoursewareEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.ChapterMapper;
import com.educloud.content.mapper.ContentRevisionMapper;
import com.educloud.content.mapper.CoursewareMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CoursewareService {

    private final CoursewareMapper coursewareMapper;
    private final ChapterMapper chapterMapper;
    private final ContentRevisionMapper revisionMapper;
    private final IdentifierGenerator idGenerator;

    @Transactional
    public CoursewareResponse addCourseware(Long chapterId, CoursewareCreateRequest req, Long teacherId) {
        ChapterEntity chapter = chapterMapper.selectById(chapterId);
        if (chapter == null || "DELETED".equals(chapter.getStatus())) {
            throw new BusinessException(ContentErrorCode.CHAPTER_NOT_FOUND, "Chapter not found");
        }

        ContentRevisionEntity revision = revisionMapper.selectById(chapter.getContentRevisionId());
        if (revision == null || !"DRAFT".equals(revision.getRevisionStatus())) {
            throw new BusinessException(ContentErrorCode.REVISION_NOT_DRAFT, "Cannot add courseware to a non-draft revision");
        }

        CoursewareEntity cw = new CoursewareEntity();
        cw.setId(idGenerator.nextId());
        cw.setContentRevisionId(chapter.getContentRevisionId());
        cw.setChapterId(chapterId);
        cw.setCourseId(chapter.getCourseId());
        cw.setTitle(req.getTitle());
        cw.setCoursewareType(req.getCoursewareType());
        cw.setFileId(req.getFileId());
        cw.setExternalUrl(req.getExternalUrl());
        cw.setDurationSeconds(req.getDurationSeconds() != null ? req.getDurationSeconds() : 0);
        cw.setSizeBytes(req.getSizeBytes() != null ? req.getSizeBytes() : 0L);
        cw.setFreePreview(Boolean.TRUE.equals(req.getFreePreview()));
        cw.setSortOrder(req.getSortOrder());
        cw.setStatus("ACTIVE");
        cw.setCreatedAt(LocalDateTime.now());
        cw.setUpdatedAt(LocalDateTime.now());
        coursewareMapper.insert(cw);

        CoursewareResponse response = new CoursewareResponse();
        response.setId(cw.getId());
        response.setChapterId(cw.getChapterId());
        response.setCourseId(cw.getCourseId());
        response.setTitle(cw.getTitle());
        response.setCoursewareType(cw.getCoursewareType());
        response.setFileId(cw.getFileId());
        response.setExternalUrl(cw.getExternalUrl());
        response.setDurationSeconds(cw.getDurationSeconds());
        response.setSizeBytes(cw.getSizeBytes());
        response.setFreePreview(cw.getFreePreview());
        response.setSortOrder(cw.getSortOrder());
        response.setCompleted(false);
        response.setPositionSeconds(0);
        return response;
    }

    @Transactional
    public CoursewareResponse updateCourseware(Long coursewareId, CoursewareUpdateRequest req, Long teacherId) {
        CoursewareEntity cw = coursewareMapper.selectById(coursewareId);
        if (cw == null || "DELETED".equals(cw.getStatus())) {
            throw new BusinessException(ContentErrorCode.COURSEWARE_NOT_FOUND, "Courseware not found");
        }

        ContentRevisionEntity revision = revisionMapper.selectById(cw.getContentRevisionId());
        if (revision == null || !"DRAFT".equals(revision.getRevisionStatus())) {
            throw new BusinessException(ContentErrorCode.REVISION_NOT_DRAFT, "Cannot update courseware in a non-draft revision");
        }

        cw.setTitle(req.getTitle());
        cw.setCoursewareType(req.getCoursewareType());
        cw.setFileId(req.getFileId());
        cw.setExternalUrl(req.getExternalUrl());
        if (req.getDurationSeconds() != null) cw.setDurationSeconds(req.getDurationSeconds());
        if (req.getSizeBytes() != null) cw.setSizeBytes(req.getSizeBytes());
        if (req.getFreePreview() != null) cw.setFreePreview(req.getFreePreview());
        if (req.getSortOrder() != null) cw.setSortOrder(req.getSortOrder());
        cw.setUpdatedAt(LocalDateTime.now());
        coursewareMapper.updateById(cw);

        CoursewareResponse response = new CoursewareResponse();
        response.setId(cw.getId());
        response.setChapterId(cw.getChapterId());
        response.setCourseId(cw.getCourseId());
        response.setTitle(cw.getTitle());
        response.setCoursewareType(cw.getCoursewareType());
        response.setFileId(cw.getFileId());
        response.setExternalUrl(cw.getExternalUrl());
        response.setDurationSeconds(cw.getDurationSeconds());
        response.setSizeBytes(cw.getSizeBytes());
        response.setFreePreview(cw.getFreePreview());
        response.setSortOrder(cw.getSortOrder());
        return response;
    }

    @Transactional
    public void deleteCourseware(Long coursewareId, Long teacherId) {
        CoursewareEntity cw = coursewareMapper.selectById(coursewareId);
        if (cw == null || "DELETED".equals(cw.getStatus())) {
            return;
        }

        ContentRevisionEntity revision = revisionMapper.selectById(cw.getContentRevisionId());
        if (revision == null || !"DRAFT".equals(revision.getRevisionStatus())) {
            throw new BusinessException(ContentErrorCode.REVISION_NOT_DRAFT, "Cannot delete courseware in a non-draft revision");
        }

        cw.setStatus("DELETED");
        cw.setUpdatedAt(LocalDateTime.now());
        coursewareMapper.updateById(cw);
    }
}
