package com.educloud.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.course.entity.CourseEnrollmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 选课数据访问（CourseEnrollmentEntity）。 */
@Mapper
public interface CourseEnrollmentMapper extends BaseMapper<CourseEnrollmentEntity> {

    /**
     * 我的课程分页（GET /me/enrollments，M05 任务 13）：course_enrollment JOIN course
     * JOIN course_version(published_version_id)，仅 ACTIVE；按 enrolled_at 倒序
     * （附 id 稳定次序）。SQL 分页由 PaginationInnerInterceptor 处理。
     */
    @Select("""
            <script>
            SELECT e.id AS enrollment_id,
                   e.course_id AS course_id,
                   e.status AS status,
                   e.enrolled_at AS enrolled_at,
                   v.title AS title,
                   v.cover_file_id AS cover_file_id
            FROM course_enrollment e
            JOIN course c ON c.id = e.course_id
            JOIN course_version v ON v.id = c.published_version_id
            WHERE e.student_id = #{studentId} AND e.status = 'ACTIVE'
            ORDER BY e.enrolled_at DESC, e.id DESC
            </script>
            """)
    IPage<CourseMyCourseRow> selectMyCoursesPage(
            Page<CourseMyCourseRow> page,
            @Param("studentId") Long studentId);

    /**
     * 教师学生列表分页（GET /courses/{id}/students，M05 任务 13）：enrollment JOIN
     * course 过滤该课程，仅 ACTIVE；按 enrolled_at 倒序（附 id 稳定次序）。
     */
    @Select("""
            <script>
            SELECT e.student_id AS student_id,
                   e.enrolled_at AS enrolled_at
            FROM course_enrollment e
            JOIN course c ON c.id = e.course_id
            WHERE e.course_id = #{courseId} AND e.status = 'ACTIVE'
            ORDER BY e.enrolled_at DESC, e.id DESC
            </script>
            """)
    IPage<CourseStudentRow> selectStudentPage(
            Page<CourseStudentRow> page,
            @Param("courseId") Long courseId);

    /**
     * 当前读（SELECT ... FOR UPDATE）重查指定选课（M05 任务 13 审查修复）：
     * DuplicateKey 兜底专用 —— MySQL REPEATABLE READ 下本事务一致读快照看不到并发
     * 事务已提交的 uk 行，重查必须用锁读（当前读）才能命中并发提交的 enrollment。
     */
    @Select("SELECT * FROM course_enrollment "
            + "WHERE course_id = #{courseId} AND student_id = #{studentId} FOR UPDATE")
    CourseEnrollmentEntity selectByCourseAndStudentForUpdate(
            @Param("courseId") Long courseId,
            @Param("studentId") Long studentId);
}
