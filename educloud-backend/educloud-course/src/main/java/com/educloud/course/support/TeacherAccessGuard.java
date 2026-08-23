package com.educloud.course.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.course.entity.CourseTeacherEntity;
import com.educloud.course.exception.CourseErrorCode;
import com.educloud.course.mapper.CourseTeacherMapper;
import org.springframework.stereotype.Service;

/**
 * 教师归属校验（M05 任务 8）：课程编辑类操作的服务内硬规则。
 *
 * <p>依据：设计规格 §9 —— 草稿读/改、学生列表、下架/归档/重上架均须
 * course_teacher 中存在 (course_id, teacher_id) 行；OWNER 与 CO_TEACHER 均可编辑
 * （本守卫不区分角色，只要求归属存在）。无归属 → COURSE_ACCESS_DENIED 403。</p>
 */
@Service
public class TeacherAccessGuard {

    private final CourseTeacherMapper courseTeacherMapper;

    public TeacherAccessGuard(CourseTeacherMapper courseTeacherMapper) {
        this.courseTeacherMapper = courseTeacherMapper;
    }

    /** 校验归属；不存在 (course_id, teacher_id) 行时抛 COURSE_ACCESS_DENIED（403）。 */
    public void requireAccess(Long courseId, Long teacherId) {
        Long count = courseTeacherMapper.selectCount(new LambdaQueryWrapper<CourseTeacherEntity>()
                .eq(CourseTeacherEntity::getCourseId, courseId)
                .eq(CourseTeacherEntity::getTeacherId, teacherId));
        if (count == null || count == 0L) {
            throw new BusinessException(CourseErrorCode.COURSE_ACCESS_DENIED);
        }
    }
}
