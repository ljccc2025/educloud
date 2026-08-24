package com.educloud.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.common.id.IdentifierGenerator;
import com.educloud.content.dto.response.ChapterResponse;
import com.educloud.content.dto.response.ContentDraftResponse;
import com.educloud.content.dto.response.CoursewareResponse;
import com.educloud.content.entity.ChapterEntity;
import com.educloud.content.entity.ContentRevisionEntity;
import com.educloud.content.entity.CourseContentEntity;
import com.educloud.content.entity.CoursewareEntity;
import com.educloud.content.entity.UserCoursewareProgressEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.ChapterMapper;
import com.educloud.content.mapper.ContentRevisionMapper;
import com.educloud.content.mapper.CourseContentMapper;
import com.educloud.content.mapper.CoursewareMapper;
import com.educloud.content.mapper.UserCoursewareProgressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseContentService {

    private final CourseContentMapper contentMapper;
    private final ContentRevisionMapper revisionMapper;
    private final ChapterMapper chapterMapper;
    private final CoursewareMapper coursewareMapper;
    private final UserCoursewareProgressMapper progressMapper;
    private final IdentifierGenerator idGenerator;

    public List<ChapterResponse> getPublishedChapters(Long courseId, Long studentId) {
        CourseContentEntity content = contentMapper.selectOne(
                new LambdaQueryWrapper<CourseContentEntity>().eq(CourseContentEntity::getCourseId, courseId));
        if (content == null || content.getPublishedRevisionId() == null) {
            return Collections.emptyList();
        }

        Long revisionId = content.getPublishedRevisionId();
        return buildChapterTree(revisionId, courseId, studentId);
    }

    @Transactional
    public ContentDraftResponse getOrCreateDraft(Long courseId, Long teacherId) {
        CourseContentEntity content = contentMapper.selectOne(
                new LambdaQueryWrapper<CourseContentEntity>().eq(CourseContentEntity::getCourseId, courseId));
        if (content == null) {
            content = new CourseContentEntity();
            content.setId(idGenerator.nextId());
            content.setCourseId(courseId);
            content.setAggregateVersion(1L);
            content.setCreatedAt(LocalDateTime.now());
            content.setUpdatedAt(LocalDateTime.now());
            contentMapper.insert(content);
        }

        List<ContentRevisionEntity> revisions = revisionMapper.selectList(
                new LambdaQueryWrapper<ContentRevisionEntity>()
                        .eq(ContentRevisionEntity::getCourseId, courseId)
                        .orderByDesc(ContentRevisionEntity::getRevisionNo));

        ContentRevisionEntity currentDraft = null;
        if (!revisions.isEmpty()) {
            ContentRevisionEntity latest = revisions.get(0);
            if ("DRAFT".equals(latest.getRevisionStatus()) || "PENDING_REVIEW".equals(latest.getRevisionStatus())) {
                currentDraft = latest;
            }
        }

        if (currentDraft == null) {
            int nextRevNo = revisions.isEmpty() ? 1 : revisions.get(0).getRevisionNo() + 1;
            currentDraft = new ContentRevisionEntity();
            currentDraft.setId(idGenerator.nextId());
            currentDraft.setCourseContentId(content.getId());
            currentDraft.setCourseId(courseId);
            currentDraft.setRevisionNo(nextRevNo);
            currentDraft.setRevisionStatus("DRAFT");
            currentDraft.setCreatedBy(teacherId);
            currentDraft.setCreatedAt(LocalDateTime.now());
            revisionMapper.insert(currentDraft);

            // Clone from published revision if exists
            if (content.getPublishedRevisionId() != null) {
                cloneChaptersAndCoursewares(content.getPublishedRevisionId(), currentDraft.getId(), courseId);
            }
        }

        ContentDraftResponse response = new ContentDraftResponse();
        response.setContentRootId(content.getId());
        response.setRevisionId(currentDraft.getId());
        response.setCourseId(courseId);
        response.setRevisionNo(currentDraft.getRevisionNo());
        response.setRevisionStatus(currentDraft.getRevisionStatus());
        response.setChapters(buildChapterTree(currentDraft.getId(), courseId, null));
        return response;
    }

    @Transactional
    public ContentDraftResponse cloneNewDraft(Long courseId, Long teacherId) {
        return getOrCreateDraft(courseId, teacherId);
    }

    private void cloneChaptersAndCoursewares(Long sourceRevisionId, Long targetRevisionId, Long courseId) {
        List<ChapterEntity> sourceChapters = chapterMapper.selectList(
                new LambdaQueryWrapper<ChapterEntity>()
                        .eq(ChapterEntity::getContentRevisionId, sourceRevisionId)
                        .eq(ChapterEntity::getStatus, "ACTIVE")
                        .orderByAsc(ChapterEntity::getSortOrder));

        for (ChapterEntity sc : sourceChapters) {
            ChapterEntity tc = new ChapterEntity();
            tc.setId(idGenerator.nextId());
            tc.setContentRevisionId(targetRevisionId);
            tc.setCourseId(courseId);
            tc.setTitle(sc.getTitle());
            tc.setDescription(sc.getDescription());
            tc.setSortOrder(sc.getSortOrder());
            tc.setStatus("ACTIVE");
            tc.setCreatedAt(LocalDateTime.now());
            tc.setUpdatedAt(LocalDateTime.now());
            chapterMapper.insert(tc);

            List<CoursewareEntity> sourceCoursewares = coursewareMapper.selectList(
                    new LambdaQueryWrapper<CoursewareEntity>()
                            .eq(CoursewareEntity::getChapterId, sc.getId())
                            .eq(CoursewareEntity::getStatus, "ACTIVE")
                            .orderByAsc(CoursewareEntity::getSortOrder));

            for (CoursewareEntity scw : sourceCoursewares) {
                CoursewareEntity tcw = new CoursewareEntity();
                tcw.setId(idGenerator.nextId());
                tcw.setContentRevisionId(targetRevisionId);
                tcw.setChapterId(tc.getId());
                tcw.setCourseId(courseId);
                tcw.setTitle(scw.getTitle());
                tcw.setCoursewareType(scw.getCoursewareType());
                tcw.setFileId(scw.getFileId());
                tcw.setExternalUrl(scw.getExternalUrl());
                tcw.setDurationSeconds(scw.getDurationSeconds());
                tcw.setSizeBytes(scw.getSizeBytes());
                tcw.setFreePreview(scw.getFreePreview());
                tcw.setSortOrder(scw.getSortOrder());
                tcw.setStatus("ACTIVE");
                tcw.setCreatedAt(LocalDateTime.now());
                tcw.setUpdatedAt(LocalDateTime.now());
                coursewareMapper.insert(tcw);
            }
        }
    }

    public List<ChapterResponse> buildChapterTree(Long revisionId, Long courseId, Long studentId) {
        List<ChapterEntity> chapters = chapterMapper.selectList(
                new LambdaQueryWrapper<ChapterEntity>()
                        .eq(ChapterEntity::getContentRevisionId, revisionId)
                        .eq(ChapterEntity::getStatus, "ACTIVE")
                        .orderByAsc(ChapterEntity::getSortOrder));

        if (chapters.isEmpty()) {
            return Collections.emptyList();
        }

        List<CoursewareEntity> coursewares = coursewareMapper.selectList(
                new LambdaQueryWrapper<CoursewareEntity>()
                        .eq(CoursewareEntity::getContentRevisionId, revisionId)
                        .eq(CoursewareEntity::getStatus, "ACTIVE")
                        .orderByAsc(CoursewareEntity::getSortOrder));

        Map<Long, UserCoursewareProgressEntity> progressMap = new HashMap<>();
        if (studentId != null) {
            List<UserCoursewareProgressEntity> progressList = progressMapper.selectList(
                    new LambdaQueryWrapper<UserCoursewareProgressEntity>()
                            .eq(UserCoursewareProgressEntity::getStudentId, studentId)
                            .eq(UserCoursewareProgressEntity::getCourseId, courseId));
            progressMap = progressList.stream()
                    .collect(Collectors.toMap(UserCoursewareProgressEntity::getCoursewareId, p -> p));
        }

        Map<Long, List<CoursewareResponse>> chapterCwMap = new HashMap<>();
        for (CoursewareEntity cw : coursewares) {
            CoursewareResponse cwr = new CoursewareResponse();
            cwr.setId(cw.getId());
            cwr.setChapterId(cw.getChapterId());
            cwr.setCourseId(cw.getCourseId());
            cwr.setTitle(cw.getTitle());
            cwr.setCoursewareType(cw.getCoursewareType());
            cwr.setFileId(cw.getFileId());
            cwr.setExternalUrl(cw.getExternalUrl());
            cwr.setDurationSeconds(cw.getDurationSeconds());
            cwr.setSizeBytes(cw.getSizeBytes());
            cwr.setFreePreview(cw.getFreePreview());
            cwr.setSortOrder(cw.getSortOrder());

            UserCoursewareProgressEntity p = progressMap.get(cw.getId());
            if (p != null) {
                cwr.setCompleted(p.getCompleted());
                cwr.setPositionSeconds(p.getPositionSeconds());
            } else {
                cwr.setCompleted(false);
                cwr.setPositionSeconds(0);
            }

            chapterCwMap.computeIfAbsent(cw.getChapterId(), k -> new ArrayList<>()).add(cwr);
        }

        List<ChapterResponse> responseList = new ArrayList<>();
        for (ChapterEntity ch : chapters) {
            ChapterResponse chr = new ChapterResponse();
            chr.setId(ch.getId());
            chr.setCourseId(ch.getCourseId());
            chr.setTitle(ch.getTitle());
            chr.setDescription(ch.getDescription());
            chr.setSortOrder(ch.getSortOrder());
            chr.setCoursewares(chapterCwMap.getOrDefault(ch.getId(), Collections.emptyList()));
            responseList.add(chr);
        }

        return responseList;
    }
}
