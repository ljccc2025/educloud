package com.educloud.course.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.course.entity.CourseTeacherEntity;
import com.educloud.course.exception.CourseErrorCode;
import com.educloud.course.mapper.CourseTeacherMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M05 任务 8：教师归属校验（TeacherAccessGuard）单元测试。
 * 依据：设计规格 §9 —— 草稿读/改均须 course_teacher（OWNER 或 CO_TEACHER）存在；
 * 不存在 → COURSE_ACCESS_DENIED 403。
 */
@ExtendWith(MockitoExtension.class)
class TeacherAccessGuardTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        // 纯 Mockito 单测渲染 LambdaQueryWrapper 的列名需要 TableInfo 缓存
        // （真实运行期由 Mapper 注册提供）；共享支持类注册。
        MybatisPlusTestSupport.registerTableInfo(CourseTeacherEntity.class);
    }

    @Mock
    private CourseTeacherMapper courseTeacherMapper;

    @Test
    void grantsAccessWhenTeacherRowExists() {
        when(courseTeacherMapper.selectCount(any())).thenReturn(1L);

        guard().requireAccess(101L, 1001L);

        assertQueryConditions(101L, 1001L);
    }

    @Test
    void deniesWhenNoTeacherRow() {
        when(courseTeacherMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> guard().requireAccess(101L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_ACCESS_DENIED);
                    assertThat(exception.errorCode().httpStatus()).isEqualTo(403);
                });
    }

    @Test
    void deniesTeacherOfAnotherCourse() {
        // 教师在课程 101 有归属，但对课程 102 无归属（跨课程访问）→ 403。
        when(courseTeacherMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> guard().requireAccess(102L, 1001L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_ACCESS_DENIED));

        assertQueryConditions(102L, 1001L);
    }

    private TeacherAccessGuard guard() {
        return new TeacherAccessGuard(courseTeacherMapper);
    }

    /** 断言查询条件：必须按 (course_id, teacher_id) 精确过滤，不允许只按 course_id 或 teacher_id。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void assertQueryConditions(Long courseId, Long teacherId) {
        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(courseTeacherMapper).selectCount(captor.capture());
        LambdaQueryWrapper<CourseTeacherEntity> wrapper = captor.getValue();
        assertThat(wrapper.getSqlSegment())
                .contains("course_id")
                .contains("teacher_id");
        assertThat(wrapper.getParamNameValuePairs())
                .containsValues(courseId, teacherId);
    }
}
