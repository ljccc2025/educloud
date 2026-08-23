package com.educloud.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.course.dto.request.CourseDraftUpdateRequest;
import com.educloud.course.dto.response.CourseDraftResponse;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseTeacherEntity;
import com.educloud.course.entity.CourseVersionEntity;
import com.educloud.course.exception.CourseErrorCode;
import com.educloud.course.mapper.CourseMapper;
import com.educloud.course.mapper.CourseTeacherMapper;
import com.educloud.course.mapper.CourseVersionMapper;
import com.educloud.course.support.SnowflakeIds;
import com.educloud.course.support.TeacherAccessGuard;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程不可变版本服务（M05 任务 8）：草稿读/复制/更新，仅 DRAFT 可原地修改。
 *
 * <p>并发防护：course_version 无 version 列（不可变版本语义，规格 V001），PUT 用
 * “version_status='DRAFT' 条件更新”（UPDATE ... WHERE id=? AND version_status='DRAFT'），
 * 影响行数 0 → VERSION_NOT_DRAFT 409；复制草稿并发撞 uk_course_version_no 时显式捕获
 * DuplicateKeyException → VERSION_NOT_DRAFT 409，根更新影响行数 0 → VERSION_CONFLICT 409
 * （参照 file FileBindingService 显式 catch 模式）。所有写操作先经
 * {@link TeacherAccessGuard} 归属校验。封面 bind 语义见任务 12，本任务只复制/存储
 * cover_file_id 值。</p>
 */
@Service
public class CourseVersionService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    private final CourseMapper courseMapper;
    private final CourseVersionMapper versionMapper;
    private final CourseTeacherMapper teacherMapper;
    private final TeacherAccessGuard teacherAccessGuard;

    public CourseVersionService(
            CourseMapper courseMapper,
            CourseVersionMapper versionMapper,
            CourseTeacherMapper teacherMapper,
            TeacherAccessGuard teacherAccessGuard) {
        this.courseMapper = courseMapper;
        this.versionMapper = versionMapper;
        this.teacherMapper = teacherMapper;
        this.teacherAccessGuard = teacherAccessGuard;
    }

    /** 当前草稿（GET /teacher/courses/{id}/draft）：归属校验后返回 course.draft_version_id；无草稿 404。 */
    public CourseDraftResponse getCurrentDraft(Long courseId, Long teacherId) {
        CourseEntity course = requireCourse(courseId);
        teacherAccessGuard.requireAccess(courseId, teacherId);
        if (course.getDraftVersionId() == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "Course has no draft version: " + courseId);
        }
        CourseVersionEntity draft = versionMapper.selectById(course.getDraftVersionId());
        if (draft == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "Course has no draft version: " + courseId);
        }
        return response(course, draft);
    }

    /**
     * 从 PUBLISHED/REJECTED 版本复制新草稿（POST /courses/{id}/drafts）：version_no+1，
     * 内容字段与 cover_file_id 继承；course.draft_version_id 切换到新草稿。
     * 已有 DRAFT 时幂等返回现有草稿；当前版本 PENDING_REVIEW 时不可复制（VERSION_NOT_DRAFT 409）。
     *
     * <p>并发兜底：两请求同时复制同一源版本会撞 uk_course_version_no —— 显式捕获
     * DuplicateKeyException → VERSION_NOT_DRAFT 409（“版本已被并发创建”，语义最贴近）；
     * course 根乐观锁更新（updateById）影响行数 0 → VERSION_CONFLICT 409。</p>
     */
    @Transactional
    public CourseDraftResponse createDraftFromPublishedOrRejected(Long courseId, Long teacherId) {
        CourseEntity course = requireCourse(courseId);
        teacherAccessGuard.requireAccess(courseId, teacherId);

        CourseVersionEntity current = course.getDraftVersionId() == null
                ? null
                : versionMapper.selectById(course.getDraftVersionId());
        if (current != null && STATUS_DRAFT.equals(current.getVersionStatus())) {
            return response(course, current);
        }
        if (current != null && STATUS_PENDING_REVIEW.equals(current.getVersionStatus())) {
            throw new BusinessException(CourseErrorCode.VERSION_NOT_DRAFT,
                    "Course version is pending review and cannot be copied");
        }

        CourseVersionEntity source = versionMapper.selectOne(new LambdaQueryWrapper<CourseVersionEntity>()
                .eq(CourseVersionEntity::getCourseId, courseId)
                .in(CourseVersionEntity::getVersionStatus, STATUS_PUBLISHED, STATUS_REJECTED)
                .orderByDesc(CourseVersionEntity::getVersionNo)
                .last("LIMIT 1"));
        if (source == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "No published or rejected version to copy: " + courseId);
        }

        CourseVersionEntity draft = new CourseVersionEntity();
        copyContent(source, draft);
        draft.setCourseId(courseId);
        draft.setVersionNo(source.getVersionNo() + 1);
        draft.setVersionStatus(STATUS_DRAFT);
        draft.setContentHash(null);
        draft.setCreatedBy(teacherId);
        draft.setCreatedAt(LocalDateTime.now());
        try {
            versionMapper.insert(draft);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(CourseErrorCode.VERSION_NOT_DRAFT,
                    "Draft version was created concurrently for course: " + courseId,
                    null, exception);
        }

        course.setDraftVersionId(draft.getId());
        int updated = courseMapper.updateById(course);
        if (updated == 0) {
            throw new BusinessException(CommonErrorCode.VERSION_CONFLICT,
                    "Course root changed concurrently: " + courseId);
        }
        return response(course, draft);
    }

    /**
     * 全量更新 DRAFT 版本（PUT /course-drafts/{versionId}）：归属校验 + version_status=DRAFT
     * 前置检查 + 当前草稿指针校验 + “DRAFT 条件更新”乐观防护（course_version 无 version 列）。
     * 校验/条件更新/响应读在同一事务。
     *
     * <p>指针校验为状态机下不可达路径的防御：正常流程中可编辑草稿只能经
     * course.draft_version_id 访问；versionId 不是当前草稿指针（孤儿/旧草稿）→
     * VERSION_NOT_DRAFT 409，不落任何 UPDATE。</p>
     */
    @Transactional
    public CourseDraftResponse updateDraft(Long versionId, Long teacherId, CourseDraftUpdateRequest request) {
        CourseVersionEntity version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND,
                    "Course version not found: " + versionId);
        }
        CourseEntity course = requireCourse(version.getCourseId());
        teacherAccessGuard.requireAccess(version.getCourseId(), teacherId);
        if (!STATUS_DRAFT.equals(version.getVersionStatus())) {
            throw new BusinessException(CourseErrorCode.VERSION_NOT_DRAFT,
                    "Only draft versions can be updated");
        }
        if (course.getDraftVersionId() == null || !course.getDraftVersionId().equals(versionId)) {
            throw new BusinessException(CourseErrorCode.VERSION_NOT_DRAFT,
                    "Version is not the current draft pointer of course: " + version.getCourseId());
        }

        Long categoryId = SnowflakeIds.parse(request.categoryId(), "categoryId");
        Long coverFileId = SnowflakeIds.parse(request.coverFileId(), "coverFileId");

        int affected = versionMapper.update(null, new LambdaUpdateWrapper<CourseVersionEntity>()
                .eq(CourseVersionEntity::getId, versionId)
                .eq(CourseVersionEntity::getVersionStatus, STATUS_DRAFT)
                .set(CourseVersionEntity::getTitle, request.title())
                .set(CourseVersionEntity::getSubtitle, request.subtitle())
                .set(CourseVersionEntity::getDescription, request.description())
                .set(CourseVersionEntity::getCoverFileId, coverFileId)
                .set(CourseVersionEntity::getLevel, request.level())
                .set(CourseVersionEntity::getPrice, request.price())
                .set(CourseVersionEntity::getCurrency, request.currency())
                .set(CourseVersionEntity::getCategoryId, categoryId));
        if (affected == 0) {
            throw new BusinessException(CourseErrorCode.VERSION_NOT_DRAFT,
                    "Course version is not in draft state");
        }

        version.setTitle(request.title());
        version.setSubtitle(request.subtitle());
        version.setDescription(request.description());
        version.setCoverFileId(coverFileId);
        version.setLevel(request.level());
        version.setPrice(request.price());
        version.setCurrency(request.currency());
        version.setCategoryId(categoryId);
        return response(course, version);
    }

    private CourseEntity requireCourse(Long courseId) {
        CourseEntity course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(CourseErrorCode.COURSE_NOT_FOUND, "Course not found: " + courseId);
        }
        return course;
    }

    private CourseDraftResponse response(CourseEntity course, CourseVersionEntity version) {
        List<CourseDraftResponse.Teacher> teachers = teacherMapper.selectList(
                        new LambdaQueryWrapper<CourseTeacherEntity>()
                                .eq(CourseTeacherEntity::getCourseId, course.getId())
                                .orderByAsc(CourseTeacherEntity::getJoinedAt))
                .stream()
                .map(teacher -> new CourseDraftResponse.Teacher(
                        String.valueOf(teacher.getTeacherId()), teacher.getTeacherRole()))
                .toList();
        return CourseDraftResponse.from(course, version, teachers);
    }

    private static void copyContent(CourseVersionEntity source, CourseVersionEntity target) {
        target.setCategoryId(source.getCategoryId());
        target.setTitle(source.getTitle());
        target.setSubtitle(source.getSubtitle());
        target.setDescription(source.getDescription());
        target.setCoverFileId(source.getCoverFileId());
        target.setLevel(source.getLevel());
        target.setPrice(source.getPrice());
        target.setCurrency(source.getCurrency());
    }
}
