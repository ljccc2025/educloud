package com.educloud.course.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.course.config.InternalApiFilter;
import com.educloud.course.dto.response.InternalCourseAccessResponse;
import com.educloud.course.dto.response.InternalCourseAccessResponse.InternalTeacherRef;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseTeacherEntity;
import com.educloud.course.exception.CourseErrorCode;
import com.educloud.course.mapper.CourseMapper;
import com.educloud.course.mapper.CourseTeacherMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * 内部课程 API 控制器（/internal/v1/**，仅服务令牌可达）。
 *
 * <p>依据：M05 设计规格 §9 —— 内部控制器只经 {@link InternalApiFilter#requireClientId}
 * 校验，不配 @PreAuthorize（服务令牌无 permissions claim）；aud=educloud-course +
 * clientId 白名单由 InternalApiFilter 在 Security 链之前 fail-closed 校验（对外流量
 * 被网关 /internal/v1/** denyAll 拦截）。GET /internal/v1/courses/{id} 返回课程
 * 归属/可见性快照（publishedVersionId / draftVersionId / ownerTeacherId /
 * contentReady 恒 false 占位 / teachers），供 M06 content 消费；课程不存在 → 404
 * COURSE_NOT_FOUND。参照 file InternalFileController 模式（filter + requireClientId
 * + 快照返回）。</p>
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalCourseController {

    private final CourseMapper courseMapper;
    private final CourseTeacherMapper courseTeacherMapper;

    public InternalCourseController(
            CourseMapper courseMapper, CourseTeacherMapper courseTeacherMapper) {
        this.courseMapper = Objects.requireNonNull(courseMapper, "courseMapper");
        this.courseTeacherMapper = Objects.requireNonNull(courseTeacherMapper, "courseTeacherMapper");
    }

    /** 课程归属/可见性快照：调用方须为 InternalApiFilter 白名单内服务（clientId）。 */
    @GetMapping("/courses/{courseId}")
    public InternalCourseAccessResponse access(
            @PathVariable Long courseId, HttpServletRequest request) {
        InternalApiFilter.requireClientId(request);

        CourseEntity course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "Course not found: " + courseId);
        }
        List<InternalTeacherRef> teachers = courseTeacherMapper.selectList(
                        new LambdaQueryWrapper<CourseTeacherEntity>()
                                .eq(CourseTeacherEntity::getCourseId, courseId)
                                .orderByAsc(CourseTeacherEntity::getJoinedAt))
                .stream()
                .map(teacher -> new InternalTeacherRef(
                        String.valueOf(teacher.getTeacherId()), teacher.getTeacherRole()))
                .toList();

        return new InternalCourseAccessResponse(
                String.valueOf(course.getId()),
                course.getLifecycleStatus(),
                course.getPublishedVersionId() == null
                        ? null : String.valueOf(course.getPublishedVersionId()),
                course.getDraftVersionId() == null
                        ? null : String.valueOf(course.getDraftVersionId()),
                String.valueOf(course.getOwnerTeacherId()),
                false,
                teachers);
    }
}
