package com.educloud.course.service;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.course.dto.response.EnrollmentResponse;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseEnrollmentEntity;
import com.educloud.course.entity.CourseTeacherEntity;
import com.educloud.course.entity.CourseVersionEntity;
import com.educloud.course.entity.OutboxEventEntity;
import com.educloud.course.mapper.CourseEnrollmentMapper;
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

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * M05 任务 13：并发选课幂等集成测试（真实 MySQL 8.0.36 Testcontainer + V000/V001）。
 *
 * <p>依据：任务 13 关键实现点 —— 锁根（SELECT ... FOR UPDATE）序列化同一课程并发
 * 选课；uk(course_id,student_id) 兜底（DuplicateKeyException → 重查返回现状）。
 * 两线程同时 POST 同一 (course, student)：最终 course_enrollment 恰一行、
 * course.enrollment_count=1、outbox 恰一条 EnrollmentCreated（幂等不重复计数、
 * 不重复发事件）。VM/CI 上以 -Pintegration 执行（本机无 Docker）。</p>
 */
@Testcontainers
class EnrollmentConcurrencyIT {

    private static final String APP_PASSWORD = "CourseApp_Test_Password_123";
    private static final long COURSE_ID = 10001L;
    private static final long VERSION_ID = 90001L;
    private static final long TEACHER_ID = 20001L;
    private static final long STUDENT_ID = 30001L;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestContainerImages.mysql())
            .withDatabaseName("educloud_course")
            .withUsername("root")
            .withPassword("root-test-password");

    private static SqlSessionTemplate sqlSessionTemplate;
    private static TransactionTemplate transactionTemplate;
    private static EnrollmentService enrollmentService;

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
                return "it-enroll-request";
            }

            @Override
            public Optional<String> traceId() {
                return Optional.of("it-enroll-trace");
            }
        };
        OutboxWriter outboxWriter = new OutboxWriter(
                sqlSessionTemplate.getMapper(OutboxEventMapper.class),
                sqlSessionTemplate.getMapper(OutboxSequenceMapper.class));
        CourseEventPublisher eventPublisher =
                new CourseEventPublisher(outboxWriter, new ObjectMapper(), requestContext);
        enrollmentService = new EnrollmentService(
                sqlSessionTemplate.getMapper(CourseMapper.class),
                sqlSessionTemplate.getMapper(CourseVersionMapper.class),
                sqlSessionTemplate.getMapper(CourseEnrollmentMapper.class),
                new TeacherAccessGuard(sqlSessionTemplate.getMapper(CourseTeacherMapper.class)),
                eventPublisher,
                mock(FileClient.class));
    }

    @BeforeEach
    void cleanDatabase() throws Exception {
        String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/educloud_course?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (Connection root = DriverManager.getConnection(url, "root", "root-test-password");
                Statement statement = root.createStatement()) {
            statement.execute("DELETE FROM course_enrollment");
            statement.execute("DELETE FROM course_teacher");
            statement.execute("DELETE FROM course_version");
            statement.execute("DELETE FROM course");
            statement.execute("DELETE FROM outbox_event");
            statement.execute("UPDATE outbox_sequence SET `last_value` = 0 WHERE source_name = 'educloud-course'");
        }
    }

    @Test
    void concurrentEnrollRequestsProduceSingleRowAndSingleCount() throws Exception {
        seedPublishedFreeCourse();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<EnrollmentResponse> enrollTask = () -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return transactionTemplate.execute(status -> enrollmentService.enroll(COURSE_ID, STUDENT_ID));
            };
            Future<EnrollmentResponse> first = executor.submit(enrollTask);
            Future<EnrollmentResponse> second = executor.submit(enrollTask);
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("both threads must be ready").isTrue();
            start.countDown();

            EnrollmentResponse firstResult = first.get(30, TimeUnit.SECONDS);
            EnrollmentResponse secondResult = second.get(30, TimeUnit.SECONDS);

            // 两个请求都成功（幂等返回现状），且指向同一行 enrollment。
            assertThat(firstResult).isNotNull();
            assertThat(secondResult).isNotNull();
            assertThat(secondResult.enrollmentId()).isEqualTo(firstResult.enrollmentId());
            assertThat(firstResult.status()).isEqualTo("ACTIVE");
            assertThat(firstResult.source()).isEqualTo("FREE");
        } finally {
            executor.shutdownNow();
        }

        CourseEnrollmentMapper enrollmentMapper = sqlSessionTemplate.getMapper(CourseEnrollmentMapper.class);
        Long rowCount = enrollmentMapper.selectCount(new LambdaQueryWrapper<CourseEnrollmentEntity>()
                .eq(CourseEnrollmentEntity::getCourseId, COURSE_ID)
                .eq(CourseEnrollmentEntity::getStudentId, STUDENT_ID));
        assertThat(rowCount).as("uk(course_id,student_id) must keep a single row").isEqualTo(1L);

        CourseEntity course = sqlSessionTemplate.getMapper(CourseMapper.class).selectById(COURSE_ID);
        assertThat(course.getEnrollmentCount()).as("enrollment_count must increment exactly once").isEqualTo(1);

        List<OutboxEventEntity> events = sqlSessionTemplate.getMapper(OutboxEventMapper.class)
                .selectList(new QueryWrapper<OutboxEventEntity>().eq("event_type", "EnrollmentCreated"));
        assertThat(events).as("exactly one EnrollmentCreated outbox row").hasSize(1);
        OutboxEventEntity event = events.get(0);
        CourseEnrollmentEntity stored = enrollmentMapper.selectOne(
                new LambdaQueryWrapper<CourseEnrollmentEntity>()
                        .eq(CourseEnrollmentEntity::getCourseId, COURSE_ID)
                        .eq(CourseEnrollmentEntity::getStudentId, STUDENT_ID));
        assertThat(stored).isNotNull();
        assertThat(event.getAggregateType()).isEqualTo("Enrollment");
        assertThat(event.getAggregateId()).isEqualTo(String.valueOf(stored.getId()));
        assertThat(event.getEventVersion()).isEqualTo(1);
        assertThat(event.getAggregateVersion()).isZero();
        JsonNode payload = new ObjectMapper().readTree(event.getPayloadJson());
        assertThat(payload.get("courseId").asLong()).isEqualTo(COURSE_ID);
        assertThat(payload.get("studentId").asLong()).isEqualTo(STUDENT_ID);
        assertThat(payload.get("source").asText()).isEqualTo("FREE");
        assertThat(event.getRequestId()).isEqualTo("it-enroll-request");
        assertThat(event.getTraceId()).isEqualTo("it-enroll-trace");
    }

    private void seedPublishedFreeCourse() {
        CourseMapper courseMapper = sqlSessionTemplate.getMapper(CourseMapper.class);
        CourseVersionMapper versionMapper = sqlSessionTemplate.getMapper(CourseVersionMapper.class);
        CourseTeacherMapper teacherMapper = sqlSessionTemplate.getMapper(CourseTeacherMapper.class);

        CourseVersionEntity version = new CourseVersionEntity();
        version.setId(VERSION_ID);
        version.setCourseId(COURSE_ID);
        version.setVersionNo(1);
        version.setCategoryId(5L);
        version.setTitle("并发选课测试课");
        version.setLevel("BEGINNER");
        version.setPrice(new BigDecimal("0.00"));
        version.setCurrency("CNY");
        version.setVersionStatus("PUBLISHED");
        version.setCreatedBy(TEACHER_ID);
        version.setCreatedAt(LocalDateTime.of(2026, 8, 23, 8, 0));
        versionMapper.insert(version);

        CourseEntity course = new CourseEntity();
        course.setId(COURSE_ID);
        course.setOwnerTeacherId(TEACHER_ID);
        course.setLifecycleStatus("PUBLISHED");
        course.setPublishedVersionId(VERSION_ID);
        course.setEnrollmentCount(0);
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
    }

    private static List<Class<?>> mapperTypes() {
        return List.of(
                CourseMapper.class,
                CourseVersionMapper.class,
                CourseEnrollmentMapper.class,
                CourseTeacherMapper.class,
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