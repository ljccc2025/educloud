package com.educloud.course;

import com.educloud.course.mapper.AuditEventMapper;
import com.educloud.course.mapper.CourseAuditSubmissionMapper;
import com.educloud.course.mapper.CourseCategoryMapper;
import com.educloud.course.mapper.CourseContentReadinessProjectionMapper;
import com.educloud.course.mapper.CourseEnrollmentMapper;
import com.educloud.course.mapper.CourseMapper;
import com.educloud.course.mapper.CourseReviewMapper;
import com.educloud.course.mapper.CourseTeacherMapper;
import com.educloud.course.mapper.CourseVersionMapper;
import com.educloud.course.mapper.OutboxEventMapper;
import com.educloud.course.mapper.OutboxSequenceMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.mockito.Mockito.mock;

/**
 * 全量上下文测试共享的外部依赖配置（M05 质量审查第 4 条）。
 *
 * <p>test profile（application-test.yml）排除 DataSource 自动配置，MyBatis-Plus Mapper
 * 不注册；本配置以 @Bean mock 补齐 Course 模块全部 Mapper（任务 9 起 10 个：
 * 8 业务 + outbox_event/outbox_sequence，供 OutboxWriter/CourseEventPublisher Bean）。
 * 后续任务新增 Mapper 时只需在此补充一个 mock Bean，CourseContextTest /
 * SecurityConfigTest 等 @SpringBootTest 全量上下文测试自动获得，无需各自 @MockBean。
 * 用法：测试类 @Import(ExternalDependencyConfiguration.class)。</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class ExternalDependencyConfiguration {

    @Bean
    public AuditEventMapper auditEventMapper() {
        return mock(AuditEventMapper.class);
    }

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

    // 任务 15 起 RabbitConfiguration 需要 ConnectionFactory 构造 RabbitTemplate
    // （test profile 排除 RabbitAutoConfiguration，不注册真实连接工厂）：mock 满足，
    // 与 educloud-file/educloud-user 上下文测试同法。
    @Bean
    public org.springframework.amqp.rabbit.connection.ConnectionFactory rabbitConnectionFactory() {
        return mock(org.springframework.amqp.rabbit.connection.ConnectionFactory.class);
    }

    @Bean
    public OutboxEventMapper outboxEventMapper() {
        return mock(OutboxEventMapper.class);
    }

    @Bean
    public OutboxSequenceMapper outboxSequenceMapper() {
        return mock(OutboxSequenceMapper.class);
    }
}
