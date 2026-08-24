package com.educloud.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.common.id.IdentifierGenerator;
import com.educloud.content.dto.request.ChapterCreateRequest;
import com.educloud.content.dto.request.ChapterUpdateRequest;
import com.educloud.content.dto.response.ChapterResponse;
import com.educloud.content.dto.response.ContentDraftResponse;
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
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChapterService {

    private final ChapterMapper chapterMapper;
    private final CoursewareMapper coursewareMapper;
    private final ContentRevisionMapper revisionMapper;
    private final CourseContentService courseContentService;
    private final IdentifierGenerator idGenerator;

    @Transactional
    public ChapterResponse addChapter(Long courseId, ChapterCreateRequest req, Long teacherId) {
        ContentDraftResponse draft = courseContentService.getOrCreateDraft(courseId, teacherId);
        if (!"DRAFT".equals(draft.getRevisionStatus())) {
            throw new BusinessException(ContentErrorCode.REVISION_NOT_DRAFT, "Cannot add chapter to a non-draft revision");
        }

        ChapterEntity chapter = new ChapterEntity();
        chapter.setId(idGenerator.nextId());
        chapter.setContentRevisionId(draft.getRevisionId());
        chapter.setCourseId(courseId);
        chapter.setTitle(req.getTitle());
        chapter.setDescription(req.getDescription());
        chapter.setSortOrder(req.getSortOrder());
        chapter.setStatus("ACTIVE");
        chapter.setCreatedAt(LocalDateTime.now());
        chapter.setUpdatedAt(LocalDateTime.now());
        chapterMapper.insert(chapter);

        ChapterResponse response = new ChapterResponse();
        response.setId(chapter.getId());
        response.setCourseId(courseId);
        response.setTitle(chapter.getTitle());
        response.setDescription(chapter.getDescription());
        response.setSortOrder(chapter.getSortOrder());
        response.setCoursewares(Collections.emptyList());
        return response;
    }

    @Transactional
    public ChapterResponse updateChapter(Long chapterId, ChapterUpdateRequest req, Long teacherId) {
        ChapterEntity chapter = chapterMapper.selectById(chapterId);
        if (chapter == null || "DELETED".equals(chapter.getStatus())) {
            throw new BusinessException(ContentErrorCode.CHAPTER_NOT_FOUND, "Chapter not found");
        }

        ContentRevisionEntity revision = revisionMapper.selectById(chapter.getContentRevisionId());
        if (revision == null || !"DRAFT".equals(revision.getRevisionStatus())) {
            throw new BusinessException(ContentErrorCode.REVISION_NOT_DRAFT, "Cannot modify chapter in a non-draft revision");
        }

        chapter.setTitle(req.getTitle());
        chapter.setDescription(req.getDescription());
        chapter.setSortOrder(req.getSortOrder());
        chapter.setUpdatedAt(LocalDateTime.now());
        chapterMapper.updateById(chapter);

        ChapterResponse response = new ChapterResponse();
        response.setId(chapter.getId());
        response.setCourseId(chapter.getCourseId());
        response.setTitle(chapter.getTitle());
        response.setDescription(chapter.getDescription());
        response.setSortOrder(chapter.getSortOrder());
        return response;
    }

    @Transactional
    public void deleteChapter(Long chapterId, Long teacherId) {
        ChapterEntity chapter = chapterMapper.selectById(chapterId);
        if (chapter == null || "DELETED".equals(chapter.getStatus())) {
            return;
        }

        ContentRevisionEntity revision = revisionMapper.selectById(chapter.getContentRevisionId());
        if (revision == null || !"DRAFT".equals(revision.getRevisionStatus())) {
            throw new BusinessException(ContentErrorCode.REVISION_NOT_DRAFT, "Cannot delete chapter from a non-draft revision");
        }

        chapter.setStatus("DELETED");
        chapter.setUpdatedAt(LocalDateTime.now());
        chapterMapper.updateById(chapter);

        // Cascade delete coursewares
        List<CoursewareEntity> coursewares = coursewareMapper.selectList(
                new LambdaQueryWrapper<CoursewareEntity>().eq(CoursewareEntity::getChapterId, chapterId));
        for (CoursewareEntity cw : coursewares) {
            cw.setStatus("DELETED");
            cw.setUpdatedAt(LocalDateTime.now());
            coursewareMapper.updateById(cw);
        }
    }
}
