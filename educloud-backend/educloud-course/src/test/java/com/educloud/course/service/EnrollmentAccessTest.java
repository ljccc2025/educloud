package com.educloud.course.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.common.api.PageResponse;
import com.educloud.common.error.BusinessException;
import com.educloud.course.dto.response.CourseStudentResponse;
import com.educloud.course.entity.CourseEnrollmentEntity;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseTeacherEntity;
import com.educloud.course.entity.CourseVersionEntity;
import com.educloud.course.exception.CourseErrorCode;
import com.educloud.course.mapper.CourseEnrollmentMapper;
import com.educloud.course.mapper.CourseMapper;
import com.educloud.course.mapper.CourseStudentRow;
import com.educloud.course.mapper.CourseTeacherMapper;
import com.educloud.course.mapper.CourseVersionMapper;
import com.educloud.course.messaging.CourseEventPublisher;
import com.educloud.course.support.MybatisPlusTestSupport;
import com.educloud.course.support.TeacherAccessGuard;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M05 任务 13：学生列表访问控制（EnrollmentService.listStudents 越权用例）。
 *
 * <p>依据：规格 §9 越权门禁用例 —— 「学生查他人课程学生列表 403」；教师学生列表
 * 须 course_teacher 归属（OWNER 或 CO_TEACHER），无归属一律 COURSE_ACCESS_DENIED 403
 * （TeacherAccessGuard 硬规则）。通过归属后按 enrolled_at 倒序分页返回
 * studentId + enrolledAt（displayName 恒 null：M05 无 user Profile 客户端，
 * 学生展示名解析留给后续接入，javadoc 已说明）。</p>
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentAccessTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MybatisPlusTestSupport.registerTableInfo(
                CourseEntity.class,
                CourseVersionEntity.class,
                CourseEnrollmentEntity.class,
                CourseTeacherEntity.class);
    }

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private CourseVersionMapper versionMapper;

    @Mock
    private CourseEnrollmentMapper enrollmentMapper;

    @Mock
    private CourseTeacherMapper courseTeacherMapper;

    @Mock
    private CourseEventPublisher eventPublisher;

    @Mock
    private FileClient fileClient;

    @Test
    void studentWithoutTeacherRowIsRejectedWith403() {
        // 学生（对目标课程无 course_teacher 归属）查学生列表 → 403，且不触达选课查询。
        when(courseTeacherMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service().listStudents(101L, 5001L, 1, 20))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_ACCESS_DENIED);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(403);
                });
        verify(enrollmentMapper, never()).selectStudentPage(any(Page.class), eq(101L));
    }

    @Test
    void nonOwningTeacherIsRejectedWith403() {
        // 教师在课程 A 有归属、对课程 B 无归属 → 跨课程访问学生列表 403。
        when(courseTeacherMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service().listStudents(102L, 1001L, 1, 20))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_ACCESS_DENIED));
        verify(enrollmentMapper, never()).selectStudentPage(any(Page.class), eq(102L));
    }

    @Test
    void owningTeacherGetsStudentPage() {
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);
        Page<CourseStudentRow> page = new Page<>(1, 20);
        page.setRecords(List.of(
                studentRow(5001L, LocalDateTime.of(2026, 8, 23, 10, 0)),
                studentRow(5002L, LocalDateTime.of(2026, 8, 23, 9, 0))));
        page.setTotal(2);
        when(enrollmentMapper.selectStudentPage(any(Page.class), eq(101L))).thenReturn(page);

        PageResponse<CourseStudentResponse> response = service().listStudents(101L, 1001L, 1, 20);

        assertThat(response.total()).isEqualTo(2);
        assertThat(response.items()).hasSize(2);
        CourseStudentResponse first = response.items().get(0);
        assertThat(first.studentId()).isEqualTo("5001");
        assertThat(first.enrolledAt()).isEqualTo(LocalDateTime.of(2026, 8, 23, 10, 0));
        // M05 无 user Profile 客户端：displayName 恒 null（javadoc 已说明，展示名后续接入）。
        assertThat(first.displayName()).isNull();
        assertThat(response.items().get(1).studentId()).isEqualTo("5002");
    }

    private EnrollmentService service() {
        return new EnrollmentService(
                courseMapper,
                versionMapper,
                enrollmentMapper,
                new TeacherAccessGuard(courseTeacherMapper),
                eventPublisher,
                fileClient);
    }

    private static CourseStudentRow studentRow(Long studentId, LocalDateTime enrolledAt) {
        CourseStudentRow row = new CourseStudentRow();
        row.setStudentId(studentId);
        row.setEnrolledAt(enrolledAt);
        return row;
    }
}
