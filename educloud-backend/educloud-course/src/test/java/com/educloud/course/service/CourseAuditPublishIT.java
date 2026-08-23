package com.educloud.course.service;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.educloud.common.error.BusinessException;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.course.dto.response.CourseAuditResponse;
import com.educloud.course.entity.CourseAuditSubmissionEntity;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseTeacherEntity;
import com.educloud.course.entity.CourseVersionEntity;
import com.educloud.course.entity.OutboxEventEntity;
import com.educloud.course.exception.CourseErrorCode;
import com.educloud.course.mapper.CourseAuditSubmissionMapper;
import com.educloud.course.mapper.CourseMapper;
import com.educloud.course.mapper.CourseTeacherMapper;
import com.educloud.course.mapper.CourseVersionMapper;
import com.educloud.course.mapper.OutboxEventMapper;
import com.educloud.course.mapper.OutboxSequenceMapper;
import com.educloud.course.messaging.CourseEventPublisher;
import com.educloud.course.messaging.OutboxWriter;
import com.educloud.course.support.TeacherAccessGuard;
import com.educloud.course.testcontainers.TestContainerImages;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M05 任务 9：课程审核原子发布集成测试（真实 MySQL 8.0.36 Testcontainer + V000/V001）。
 *
 * <p>以 MyBatis-Plus（Spring 事务工厂 + 分页/乐观锁拦截器）+ DataSourceTransactionManager
 * 驱动真实 {@link CourseAuditService}：提交→审批在同一事务提交后断言 published_version_id
 * 切换、旧发布版本 SUPERSEDED、lifecycle=PUBLISHED、outbox_event 落一条 CoursePublished
 * 且信封字段正确（aggregateType=Course、aggregateId=courseId、派生 routing key
 * Course.{courseId}）；自审失败路径断言提交/outbox 与业务写同事务回滚。
 * VM/CI 上以 -Pintegration 执行（本机无 Docker）。</p>
 */
@Testcontainers
class CourseAuditPublishIT {

    private static final String APP_PASSWORD = "CourseApp_Test_Password_123";
    private static final long COURSE_ID = 10001L;
    private static final long PUBLISHED_VERSION_ID = 90001L;
    private static final long DRAFT_VERSION_ID = 90002L;
    private static final long TEACHER_ID = 20001L;
    private static final long REVIEWER_ID = 30001L;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestContainerImages.mysql())
            .withDatabaseName("educloud_course")
            .withUsername("root")
            .withPassword("root-test-password");

    private static SqlSessionTemplate sqlSessionTemplate;
    private static TransactionTemplate transactionTemplate;
    private static CourseAuditService auditService;

    @BeforeAll
    static void bootstrap() throws Exception {
        String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/educloud_course?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

        try (Connection root = DriverManager.getConnection(url, "root", "root-test-password");
                Statement statement = root.createStatement()) {
            statement.execute("CREATE USER IF NOT EXISTS 'course_app'@'%' IDENTIFIED BY '" + APP_PASSWORD + "'");
            Path sqlDir = migrationDirectory();
            for (String script : List.of("V000__technical_tables.sql", "V001__course.sql")) {
                ScriptUtils.executeSqlScript(root, new FileSystemResource(sqlDir.resolve(script).toFile()));
            }
        }

        PooledDataSource dataSource = new PooledDataSource(
                "com.mysql.cj.jdbc.Driver", url, "course_app", APP_PASSWORD);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        factoryBean.setPlugins(interceptor);
        org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        for (Class<?> mapperType : mapperTypes()) {
            sqlSessionFactory.getConfiguration().addMapper(mapperType);
        }
        sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);

        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        transactionTemplate = new TransactionTemplate(transactionManager);

        RequestContextAccessor requestContext = new RequestContextAccessor() {
            @Override
            public String requestId() {
                return "it-request";
            }

            @Override
            public Optional<String> traceId() {
                return Optional.of("it-trace");
            }
        };
        OutboxWriter outboxWriter = new OutboxWriter(
                sqlSessionTemplate.getMapper(OutboxEventMapper.class),
                sqlSessionTemplate.getMapper(OutboxSequenceMapper.class));
        CourseEventPublisher eventPublisher =
                new CourseEventPublisher(outboxWriter, new ObjectMapper(), requestContext);
        auditService = new CourseAuditService(
                sqlSessionTemplate.getMapper(CourseMapper.class),
                sqlSessionTemplate.getMapper(CourseVersionMapper.class),
                sqlSessionTemplate.getMapper(CourseAuditSubmissionMapper.class),
                new TeacherAccessGuard(sqlSessionTemplate.getMapper(CourseTeacherMapper.class)),
                eventPublisher);
    }

    @BeforeEach
    void cleanDatabase() throws Exception {
        String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/educloud_course?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (Connection root = DriverManager.getConnection(url, "root", "root-test-password");
                Statement statement = root.createStatement()) {
            statement.execute("DELETE FROM course_audit_submission");
            statement.execute("DELETE FROM course_version");
            statement.execute("DELETE FROM course_teacher");
            statement.execute("DELETE FROM course");
            statement.execute("DELETE FROM outbox_event");
            statement.execute("UPDATE outbox_sequence SET `last_value` = 0 WHERE source_name = 'educloud-course'");
        }
    }

    @Test
    void submitThenApprovePublishesVersionSupersedesOldAndWritesOutboxInOneTransaction() throws Exception {
        Long auditId = transactionTemplate.execute(status -> {
            seedPublishedCourseAndNewDraft();
            CourseAuditResponse submitted = auditService.submitForReview(DRAFT_VERSION_ID, TEACHER_ID);
            assertThat(submitted.submissionStatus()).isEqualTo("PENDING");
            CourseAuditResponse approved = auditService.approve(Long.parseLong(submitted.auditId()), REVIEWER_ID);
            assertThat(approved.submissionStatus()).isEqualTo("APPROVED");
            return Long.parseLong(approved.auditId());
        });

        CourseMapper courseMapper = sqlSessionTemplate.getMapper(CourseMapper.class);
        CourseEntity course = courseMapper.selectById(COURSE_ID);
        assertThat(course.getPublishedVersionId()).isEqualTo(DRAFT_VERSION_ID);
        assertThat(course.getLifecycleStatus()).isEqualTo("PUBLISHED");
        assertThat(course.getPublishedAt()).isNotNull();
        assertThat(course.getDraftVersionId()).isNull();
        // 根乐观锁自动递增：seed version=0 → submit 0→1 → approve 1→2（M04 坑 4：禁止手动 +1）。
        assertThat(course.getVersion()).isEqualTo(2L);

        CourseVersionMapper versionMapper = sqlSessionTemplate.getMapper(CourseVersionMapper.class);
        assertThat(versionMapper.selectById(PUBLISHED_VERSION_ID).getVersionStatus()).isEqualTo("SUPERSEDED");
        assertThat(versionMapper.selectById(DRAFT_VERSION_ID).getVersionStatus()).isEqualTo("PUBLISHED");

        CourseAuditSubmissionEntity submission =
                sqlSessionTemplate.getMapper(CourseAuditSubmissionMapper.class).selectById(auditId);
        assertThat(submission.getStatus()).isEqualTo("APPROVED");
        assertThat(submission.getReviewedBy()).isEqualTo(REVIEWER_ID);
        assertThat(submission.getReviewedAt()).isNotNull();

        // Outbox：同事务恰好落一条 CoursePublished，信封字段正确（aggregateType/aggregateId，
        // routing key 由 aggregateType.aggregateId 派生，任务 15 dispatcher 消费）。
        List<OutboxEventEntity> events = sqlSessionTemplate.getMapper(OutboxEventMapper.class)
                .selectList(new QueryWrapper<OutboxEventEntity>().eq("event_type", "CoursePublished"));
        assertThat(events).hasSize(1);
        OutboxEventEntity event = events.get(0);
        assertThat(event.getAggregateType()).isEqualTo("Course");
        assertThat(event.getAggregateId()).isEqualTo(String.valueOf(COURSE_ID));
        assertThat(event.getEventType()).isEqualTo("CoursePublished");
        assertThat(event.getEventVersion()).isEqualTo(1);
        // aggregateVersion 取 approve 回写后的根版本（=2，见上 course.version 断言）。
        assertThat(event.getAggregateVersion()).isEqualTo(2L);
        assertThat(event.getPublishStatus()).isEqualTo("PENDING");
        assertThat(event.getSourceSequence()).isEqualTo(1L);
        assertThat(event.getAggregateType() + "." + event.getAggregateId()).isEqualTo("Course.10001");
        // payload_json 是 MySQL JSON 列：MySQL 8 写入时会把 JSON 规范化（冒号/逗号后带空格），
        // 字符串 contains 断言依赖空白格式，改为解析 JSON 后按字段断言（对规范化输出稳定）。
        JsonNode payload = new ObjectMapper().readTree(event.getPayloadJson());
        assertThat(payload.get("courseId").asLong()).isEqualTo(COURSE_ID);
        assertThat(payload.get("versionId").asLong()).isEqualTo(DRAFT_VERSION_ID);
        assertThat(payload.get("publishedAt").asText()).isNotBlank();
        assertThat(event.getRequestId()).isEqualTo("it-request");
        assertThat(event.getTraceId()).isEqualTo("it-trace");
    }

    @Test
    void failedApproveRollsBackSubmitAndOutboxWriteInSameTransaction() {
        // seed 在事务之外先落库提交：回滚只作用于事务内的 submit+approve，
        // 否则 seed 行随自审异常一起回滚，事务外 selectById 拿到 null 导致 NPE。
        seedPublishedCourseAndNewDraft();
        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            CourseAuditResponse submitted = auditService.submitForReview(DRAFT_VERSION_ID, TEACHER_ID);
            // 自审拒绝 403：提交教师同时作为审核人 → 异常回滚整个提交+发布事务。
            auditService.approve(Long.parseLong(submitted.auditId()), TEACHER_ID);
            return null;
        })).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(CourseErrorCode.COURSE_ACCESS_DENIED));

        CourseEntity course = sqlSessionTemplate.getMapper(CourseMapper.class).selectById(COURSE_ID);
        assertThat(course.getLifecycleStatus()).isEqualTo("DRAFT");
        assertThat(course.getDraftVersionId()).isEqualTo(DRAFT_VERSION_ID);
        assertThat(course.getVersion()).isZero();

        CourseVersionMapper versionMapper = sqlSessionTemplate.getMapper(CourseVersionMapper.class);
        assertThat(versionMapper.selectById(DRAFT_VERSION_ID).getVersionStatus()).isEqualTo("DRAFT");
        assertThat(versionMapper.selectById(PUBLISHED_VERSION_ID).getVersionStatus()).isEqualTo("PUBLISHED");

        assertThat(sqlSessionTemplate.getMapper(CourseAuditSubmissionMapper.class)
                .selectCount(new QueryWrapper<>())).isZero();
        assertThat(sqlSessionTemplate.getMapper(OutboxEventMapper.class)
                .selectCount(new QueryWrapper<>())).isZero();
    }

    private void seedPublishedCourseAndNewDraft() {
        CourseMapper courseMapper = sqlSessionTemplate.getMapper(CourseMapper.class);
        CourseVersionMapper versionMapper = sqlSessionTemplate.getMapper(CourseVersionMapper.class);
        CourseTeacherMapper teacherMapper = sqlSessionTemplate.getMapper(CourseTeacherMapper.class);

        CourseEntity course = new CourseEntity();
        course.setId(COURSE_ID);
        course.setOwnerTeacherId(TEACHER_ID);
        course.setLifecycleStatus("DRAFT");
        course.setPublishedVersionId(PUBLISHED_VERSION_ID);
        course.setDraftVersionId(DRAFT_VERSION_ID);
        course.setVersion(0L);
        course.setCreatedBy(TEACHER_ID);
        course.setCreatedAt(LocalDateTime.of(2026, 8, 23, 8, 0));
        course.setUpdatedBy(TEACHER_ID);
        course.setUpdatedAt(LocalDateTime.of(2026, 8, 23, 8, 0));
        courseMapper.insert(course);

        CourseTeacherEntity teacher = new CourseTeacherEntity();
        teacher.setId(1L);
        teacher.setCourseId(COURSE_ID);
        teacher.setTeacherId(TEACHER_ID);
        teacher.setTeacherRole("OWNER");
        teacher.setJoinedAt(LocalDateTime.of(2026, 8, 23, 8, 0));
        teacherMapper.insert(teacher);

        versionMapper.insert(versionRow(PUBLISHED_VERSION_ID, 1, "PUBLISHED", "已发布旧版"));
        versionMapper.insert(versionRow(DRAFT_VERSION_ID, 2, "DRAFT", "待发布新版"));
    }

    private static CourseVersionEntity versionRow(Long id, int versionNo, String status, String title) {
        CourseVersionEntity entity = new CourseVersionEntity();
        entity.setId(id);
        entity.setCourseId(COURSE_ID);
        entity.setVersionNo(versionNo);
        entity.setCategoryId(5L);
        entity.setTitle(title);
        entity.setLevel("BEGINNER");
        entity.setPrice(new java.math.BigDecimal("99.00"));
        entity.setCurrency("CNY");
        entity.setVersionStatus(status);
        entity.setCreatedBy(TEACHER_ID);
        entity.setCreatedAt(LocalDateTime.of(2026, 8, 23, 8, 0));
        return entity;
    }

    private static List<Class<?>> mapperTypes() {
        return List.of(
                CourseMapper.class,
                CourseVersionMapper.class,
                CourseTeacherMapper.class,
                CourseAuditSubmissionMapper.class,
                OutboxEventMapper.class,
                OutboxSequenceMapper.class);
    }

    private static Path migrationDirectory() {
        Path candidate = Path.of(System.getProperty("user.dir"), "..", "..", "deploy", "sql", "course");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        throw new IllegalStateException(
                "migration directory not found at " + candidate.toAbsolutePath()
                        + "; run failsafe from the educloud-course module directory");
    }
}
