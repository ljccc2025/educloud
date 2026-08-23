package com.educloud.course.service;

import com.educloud.course.dto.request.CourseCreateRequest;
import com.educloud.course.dto.response.CourseDraftResponse;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseTeacherEntity;
import com.educloud.course.entity.CourseVersionEntity;
import com.educloud.course.mapper.CourseMapper;
import com.educloud.course.mapper.CourseTeacherMapper;
import com.educloud.course.mapper.CourseVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程聚合根服务（M05 任务 8）：建课（根 + 负责人关系 + 首版草稿同一事务）。
 *
 * <p>生命周期/下架/归档等根操作在任务 10 接入本服务（锁根 + outbox）。封面 bind
 * 见任务 12（FileClient），本任务只存 cover_file_id 值。</p>
 */
@Service
public class CourseService {

    public static final String LIFECYCLE_DRAFT = "DRAFT";
    public static final String TEACHER_ROLE_OWNER = "OWNER";

    private final CourseMapper courseMapper;
    private final CourseTeacherMapper courseTeacherMapper;
    private final CourseVersionMapper courseVersionMapper;

    public CourseService(
            CourseMapper courseMapper,
            CourseTeacherMapper courseTeacherMapper,
            CourseVersionMapper courseVersionMapper) {
        this.courseMapper = courseMapper;
        this.courseTeacherMapper = courseTeacherMapper;
        this.courseVersionMapper = courseVersionMapper;
    }

    /**
     * 建课：同一事务插入 course（owner=当前教师，lifecycle=DRAFT）+ course_teacher(OWNER)
     * + course_version(version_no=1, DRAFT)；course.draft_version_id 指向新版本。
     * 主键由 MyBatis-Plus ASSIGN_ID 在 insert 时生成并回写实体。
     */
    @Transactional
    public CourseDraftResponse createCourse(Long teacherId, CourseCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();

        CourseEntity course = new CourseEntity();
        course.setOwnerTeacherId(teacherId);
        course.setLifecycleStatus(LIFECYCLE_DRAFT);
        // 与 course.version DEFAULT 0 对齐：insert 后立即可携带明确乐观锁条件更新根。
        course.setVersion(0L);
        course.setCreatedBy(teacherId);
        course.setCreatedAt(now);
        course.setUpdatedBy(teacherId);
        course.setUpdatedAt(now);
        courseMapper.insert(course);

        CourseTeacherEntity teacher = new CourseTeacherEntity();
        teacher.setCourseId(course.getId());
        teacher.setTeacherId(teacherId);
        teacher.setTeacherRole(TEACHER_ROLE_OWNER);
        teacher.setJoinedAt(now);
        courseTeacherMapper.insert(teacher);

        CourseVersionEntity draft = new CourseVersionEntity();
        draft.setCourseId(course.getId());
        draft.setVersionNo(1);
        draft.setCategoryId(request.categoryId());
        draft.setTitle(request.title());
        draft.setSubtitle(request.subtitle());
        draft.setDescription(request.description());
        draft.setCoverFileId(request.coverFileId());
        draft.setLevel(request.level());
        draft.setPrice(request.price());
        draft.setCurrency(request.currency());
        draft.setVersionStatus(CourseVersionService.STATUS_DRAFT);
        draft.setCreatedBy(teacherId);
        draft.setCreatedAt(now);
        courseVersionMapper.insert(draft);

        course.setDraftVersionId(draft.getId());
        courseMapper.updateById(course);

        return CourseDraftResponse.from(course, draft,
                List.of(new CourseDraftResponse.Teacher(String.valueOf(teacherId), TEACHER_ROLE_OWNER)));
    }
}
