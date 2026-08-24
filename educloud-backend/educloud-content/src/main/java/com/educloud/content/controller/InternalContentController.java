package com.educloud.content.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.content.dto.response.CourseContentReadinessResponse;
import com.educloud.content.entity.CourseContentEntity;
import com.educloud.content.entity.CoursewareEntity;
import com.educloud.content.mapper.CourseContentMapper;
import com.educloud.content.mapper.CoursewareMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/courses")
@RequiredArgsConstructor
public class InternalContentController {

    private final CourseContentMapper contentMapper;
    private final CoursewareMapper coursewareMapper;

    @GetMapping("/{courseId}/content/readiness")
    public CourseContentReadinessResponse getContentReadiness(@PathVariable Long courseId) {
        CourseContentEntity content = contentMapper.selectOne(
                new LambdaQueryWrapper<CourseContentEntity>().eq(CourseContentEntity::getCourseId, courseId));

        CourseContentReadinessResponse resp = new CourseContentReadinessResponse();
        resp.setCourseId(courseId);

        if (content == null || content.getPublishedRevisionId() == null) {
            resp.setContentRootId(content != null ? content.getId() : null);
            resp.setPublishedRevisionId(null);
            resp.setReady(false);
            resp.setAggregateVersion(content != null ? content.getAggregateVersion() : 0L);
            return resp;
        }

        long activeCoursewares = coursewareMapper.selectCount(
                new LambdaQueryWrapper<CoursewareEntity>()
                        .eq(CoursewareEntity::getContentRevisionId, content.getPublishedRevisionId())
                        .eq(CoursewareEntity::getStatus, "ACTIVE"));

        resp.setContentRootId(content.getId());
        resp.setPublishedRevisionId(content.getPublishedRevisionId());
        resp.setReady(activeCoursewares > 0);
        resp.setAggregateVersion(content.getAggregateVersion());
        return resp;
    }
}
