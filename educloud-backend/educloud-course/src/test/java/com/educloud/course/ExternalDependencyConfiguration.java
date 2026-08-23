package com.educloud.course;

import com.educloud.course.mapper.CourseAuditSubmissionMapper;
import com.educloud.course.mapper.CourseCategoryMapper;
import com.educloud.course.mapper.CourseContentReadinessProjectionMapper;
import com.educloud.course.mapper.CourseEnrollmentMapper;
import com.educloud.course.mapper.CourseMapper;
import com.educloud.course.mapper.CourseReviewMapper;
import com.educloud.course.mapper.CourseTeacherMapper;
import com.educloud.course.mapper.CourseVersionMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.mockito.Mockito.mock;

/**
 * 全量上下文测试共享的外部依赖配置（M05 质量审查第 4 条）。
 *
 * <p>test profile（application-test.yml）排除 DataSource 自动配置，MyBatis-Plus Mapper
 * 不注册；本配置以 @Bean mock 补齐 Course 模块全部 Mapper（当前 8 个，任务 4 建立）。
 * 后续任务新增 Mapper 时只需在此补充一个 mock Bean，CourseContextTest /
 * SecurityConfigTest 等 @SpringBootTest 全量上下文测试自动获得，无需各自 @MockBean。
 * 用法：测试类 @Import(ExternalDependencyConfiguration.class)。</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class ExternalDependencyConfiguration {

    @Bean
    public CourseAuditSubmissionMapper courseAuditSubmissionMapper() {
        return mock(CourseAuditSubmissionMapper.class);
    }

    @Bean
    public CourseCategoryMapper courseCategoryMapper() {
        return mock(CourseCategoryMapper.class);
    }

    @Bean
    public CourseContentReadinessProjectionMapper courseContentReadinessProjectionMapper() {
        return mock(CourseContentReadinessProjectionMapper.class);
    }

    @Bean
    public CourseEnrollmentMapper courseEnrollmentMapper() {
        return mock(CourseEnrollmentMapper.class);
    }

    @Bean
    public CourseMapper courseMapper() {
        return mock(CourseMapper.class);
    }

    @Bean
    public CourseReviewMapper courseReviewMapper() {
        return mock(CourseReviewMapper.class);
    }

    @Bean
    public CourseTeacherMapper courseTeacherMapper() {
        return mock(CourseTeacherMapper.class);
    }

    @Bean
    public CourseVersionMapper courseVersionMapper() {
        return mock(CourseVersionMapper.class);
    }
}
