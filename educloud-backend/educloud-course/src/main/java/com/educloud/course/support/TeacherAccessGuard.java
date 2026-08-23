package com.educloud.course.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.course.entity.CourseTeacherEntity;
import com.educloud.course.exception.CourseErrorCode;
import com.educloud.course.mapper.CourseTeacherMapper;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 教师归属校验（M05 任务 8）：课程编辑类操作的服务内硬规则。
 *
 * <p>依据：设计规格 §9 —— 草稿读/改、学生列表、下架/归档/重上架均须
 * course_teacher 中存在 (course_id, teacher_id) 行；OWNER 与 CO_TEACHER 均可编辑
 * （本守卫不区分角色，只要求归属存在）。无归属 → COURSE_ACCESS_DENIED 403。</p>
 *
 * <p>M05 任务 23 补充：管理角色（SYSTEM_ADMIN/SUPER_ADMIN，V004 已挂全部 course:* 权限
 * 码）经 @PreAuthorize 门禁后，管理端对任意课程执行下架/重上架/归档时不再要求
 * course_teacher 归属（管理员不是任课教师，规格 §6 管理查询/操作语义）。普通教师
 * （含 COURSE_REVIEWER）仍走归属硬校验。</p>
 */
@Service
public class TeacherAccessGuard {

    /** 管理内置角色（V001 seed role_id 6/7，JWT roles claim 角色码）。 */
    public static final Set<String> ADMIN_ROLES = Set.of("SYSTEM_ADMIN", "SUPER_ADMIN");

    private final CourseTeacherMapper courseTeacherMapper;

    public TeacherAccessGuard(CourseTeacherMapper courseTeacherMapper) {
        this.courseTeacherMapper = courseTeacherMapper;
    }

    /** 校验归属；不存在 (course_id, teacher_id) 行时抛 COURSE_ACCESS_DENIED（403）。 */
    public void requireAccess(Long courseId, Long teacherId) {
        requireAccess(courseId, teacherId, Set.of());
    }

    /** 归属校验（角色感知）：管理角色放行（不查归属表），其余照常硬校验。 */
    public void requireAccess(Long courseId, Long teacherId, Set<String> roles) {
        if (roles != null && roles.stream().anyMatch(ADMIN_ROLES::contains)) {
            return;
        }
        Long count = courseTeacherMapper.selectCount(new LambdaQueryWrapper<CourseTeacherEntity>()
                .eq(CourseTeacherEntity::getCourseId, courseId)
                .eq(CourseTeacherEntity::getTeacherId, teacherId));
        if (count == null || count == 0L) {
            throw new BusinessException(CourseErrorCode.COURSE_ACCESS_DENIED);
        }
    }
}
