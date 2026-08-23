package com.educloud.course.service;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.educloud.course.dto.request.ReviewUpsertRequest;
import com.educloud.course.entity.CourseEnrollmentEntity;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseReviewEntity;
import com.educloud.course.mapper.CourseEnrollmentMapper;
import com.educloud.course.mapper.CourseMapper;
import com.educloud.course.mapper.CourseReviewMapper;
import com.educloud.course.testcontainers.TestContainerImages;
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
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M05 任务 14：评价评分汇总一致性集成测试（真实 MySQL 8.0.36 Testcontainer + V000/V001）。
 *
 * <p>以 MyBatis-Plus（分页/乐观锁拦截器）+ DataSourceTransactionManager 驱动真实
 * {@link CourseReviewService}：多学生 upsert 后断言 course.rating_avg/rating_count 等于
 * VISIBLE 评价聚合（HIDDEN 不计入）；管理角色隐藏一条后重算正确且重复隐藏幂等；
 * 首评归一化 5.00/1、隐藏最后一条 VISIBLE 归一化 0.00/0（P3 归一化锁定）；
 * 学生改分（5→2）后隐藏不覆盖新评分（P1b 窄更新端到端）；
 * 并发 upsert（course 根行锁串行化 + 同事务聚合）后汇总一致。VM/CI 上以
 * -Pintegration 执行（本机无 Docker）。</p>
 */
@Testcontainers
class ReviewSummaryIT {

    private static final String APP_PASSWORD = "CourseApp_Test_Password_123";
    private static final long COURSE_ID = 10001L;
    private static final long OWNER_TEACHER_ID = 20001L;
    private static final long STUDENT_1 = 50001L;
    private static final long STUDENT_2 = 50002L;
    private static final long STUDENT_3 = 50003L;
    private static final long ADMIN_ID = 30001L;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestContainerImages.mysql())
            .withDatabaseName("educloud_course")
            .withUsername("root")
            .withPassword("root-test-password");

    private static SqlSessionTemplate sqlSessionTemplate;
    private static TransactionTemplate transactionTemplate;
    private static CourseReviewService reviewService;
    private static CourseMapper courseMapper;

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

        courseMapper = sqlSessionTemplate.getMapper(CourseMapper.class);
        reviewService = new CourseReviewService(
                courseMapper,
                sqlSessionTemplate.getMapper(CourseEnrollmentMapper.class),
                sqlSessionTemplate.getMapper(CourseReviewMapper.class));
    }

    @BeforeEach
    void cleanDatabase() throws Exception {
        String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/educloud_course?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (Connection root = DriverManager.getConnection(url, "root", "root-test-password");
                Statement statement = root.createStatement()) {
            statement.execute("DELETE FROM course_review");
            statement.execute("DELETE FROM course_enrollment");
            statement.execute("DELETE FROM course");
        }
    }

    @Test
    void upsertKeepsRatingSummaryConsistentAcrossStudentsAndUpdates() {
        seedCourseAndEnrollments(List.of(STUDENT_1, STUDENT_2, STUDENT_3));
        transactionTemplate.execute(status -> {
            reviewService.upsert(COURSE_ID, STUDENT_1, new ReviewUpsertRequest(5, "很好"));
            reviewService.upsert(COURSE_ID, STUDENT_2, new ReviewUpsertRequest(4, "不错"));
            reviewService.upsert(COURSE_ID, STUDENT_3, new ReviewUpsertRequest(3, "一般"));
            return null;
        });

        CourseEntity course = courseMapper.selectById(COURSE_ID);
        assertThat(course.getRatingAvg()).isEqualByComparingTo("4.00");
        assertThat(course.getRatingCount()).isEqualTo(3);

        // upsert 更新自己评价：student1 5→1，汇总变为 (1+4+3)/3=2.666… → DECIMAL(3,2) 2.67。
        transactionTemplate.execute(status -> {
            reviewService.upsert(COURSE_ID, STUDENT_1, new ReviewUpsertRequest(1, "改一星"));
            return null;
        });
        course = courseMapper.selectById(COURSE_ID);
        assertThat(course.getRatingAvg()).isEqualByComparingTo("2.67");
        assertThat(course.getRatingCount()).isEqualTo(3);

        // uk(course_id, student_id)：仍只有 3 条评价行。
        assertThat(sqlSessionTemplate.getMapper(CourseReviewMapper.class)
                .selectCount(new LambdaQueryWrapper<>())).isEqualTo(3L);
    }

    @Test
    void hideRecalculatesSummaryAndIsIdempotent() {
        seedCourseAndEnrollments(List.of(STUDENT_1, STUDENT_2));
        transactionTemplate.execute(status -> {
            reviewService.upsert(COURSE_ID, STUDENT_1, new ReviewUpsertRequest(5, "很好"));
            reviewService.upsert(COURSE_ID, STUDENT_2, new ReviewUpsertRequest(4, "不错"));
            return null;
        });
        assertThat(courseMapper.selectById(COURSE_ID).getRatingAvg()).isEqualByComparingTo("4.50");
        assertThat(courseMapper.selectById(COURSE_ID).getRatingCount()).isEqualTo(2);

        Long reviewId = reviewIdOf(STUDENT_1);
        transactionTemplate.execute(status -> {
            reviewService.hide(reviewId, ADMIN_ID, Set.of("SYSTEM_ADMIN"));
            return null;
        });

        // 软隐藏保留审计行；VISIBLE 聚合重算 → 只剩 student2 的 4 分。
        assertThat(sqlSessionTemplate.getMapper(CourseReviewMapper.class)
                .selectById(reviewId).getStatus()).isEqualTo("HIDDEN");
        CourseEntity course = courseMapper.selectById(COURSE_ID);
        assertThat(course.getRatingAvg()).isEqualByComparingTo("4.00");
        assertThat(course.getRatingCount()).isEqualTo(1);

        // 已隐藏重复删幂等：不改变现状。
        transactionTemplate.execute(status -> {
            reviewService.hide(reviewId, ADMIN_ID, Set.of("SUPER_ADMIN"));
            return null;
        });
        course = courseMapper.selectById(COURSE_ID);
        assertThat(course.getRatingAvg()).isEqualByComparingTo("4.00");
        assertThat(course.getRatingCount()).isEqualTo(1);
    }

    @Test
    void concurrentUpsertsKeepSummaryConsistent() throws Exception {
        seedCourseAndEnrollments(List.of(STUDENT_1, STUDENT_2));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> first = pool.submit(() -> {
                start.await();
                transactionTemplate.execute(status -> {
                    reviewService.upsert(COURSE_ID, STUDENT_1, new ReviewUpsertRequest(5, "c1"));
                    return null;
                });
                return null;
            });
            Future<?> second = pool.submit(() -> {
                start.await();
                transactionTemplate.execute(status -> {
                    reviewService.upsert(COURSE_ID, STUDENT_2, new ReviewUpsertRequest(3, "c2"));
                    return null;
                });
                return null;
            });
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // course 根行锁串行化两个 upsert：后提交事务的聚合看到前一个已提交行 → 汇总不丢。
        CourseEntity course = courseMapper.selectById(COURSE_ID);
        assertThat(course.getRatingAvg()).isEqualByComparingTo("4.00");
        assertThat(course.getRatingCount()).isEqualTo(2);
    }

    @Test
    void firstReviewNormalizesSummaryTo5_00And1() {
        // P3 归一化：首条 VISIBLE 评价 → rating_avg=5.00 / rating_count=1。
        seedCourseAndEnrollments(List.of(STUDENT_1));
        transactionTemplate.execute(status -> {
            reviewService.upsert(COURSE_ID, STUDENT_1, new ReviewUpsertRequest(5, "首评"));
            return null;
        });

        CourseEntity course = courseMapper.selectById(COURSE_ID);
        assertThat(course.getRatingAvg()).isEqualByComparingTo("5.00");
        assertThat(course.getRatingCount()).isEqualTo(1);
    }

    @Test
    void hidingLastVisibleReviewNormalizesSummaryTo0_00And0() {
        // P3 归一化：隐藏最后一条 VISIBLE 后无可见评价 → rating_avg=0.00 / rating_count=0
        // （AVG 聚合为 NULL，服务层归一化写 0.00，而非残留旧均值）。
        seedCourseAndEnrollments(List.of(STUDENT_1));
        transactionTemplate.execute(status -> {
            reviewService.upsert(COURSE_ID, STUDENT_1, new ReviewUpsertRequest(5, "唯一"));
            return null;
        });
        Long reviewId = reviewIdOf(STUDENT_1);
        transactionTemplate.execute(status -> {
            reviewService.hide(reviewId, ADMIN_ID, Set.of("SYSTEM_ADMIN"));
            return null;
        });

        CourseEntity course = courseMapper.selectById(COURSE_ID);
        assertThat(course.getRatingAvg()).isEqualByComparingTo("0.00");
        assertThat(course.getRatingCount()).isEqualTo(0);
    }

    @Test
    void hideAfterStudentRerateKeepsNewRating() {
        // P1b 端到端：学生先评 5 分再改 2 分，管理端随后隐藏 —— 窄更新只写
        // status/updated_by/updated_at（WHERE id=?），库中 rating/content 保持 2 分/新内容。
        seedCourseAndEnrollments(List.of(STUDENT_1));
        transactionTemplate.execute(status -> {
            reviewService.upsert(COURSE_ID, STUDENT_1, new ReviewUpsertRequest(5, "第一次"));
            reviewService.upsert(COURSE_ID, STUDENT_1, new ReviewUpsertRequest(2, "改两星"));
            return null;
        });
        Long reviewId = reviewIdOf(STUDENT_1);
        transactionTemplate.execute(status -> {
            reviewService.hide(reviewId, ADMIN_ID, Set.of("SYSTEM_ADMIN"));
            return null;
        });

        CourseReviewEntity row = sqlSessionTemplate.getMapper(CourseReviewMapper.class)
                .selectById(reviewId);
        assertThat(row.getRating()).isEqualTo(2);
        assertThat(row.getContent()).isEqualTo("改两星");
        assertThat(row.getStatus()).isEqualTo("HIDDEN");
        CourseEntity course = courseMapper.selectById(COURSE_ID);
        assertThat(course.getRatingAvg()).isEqualByComparingTo("0.00");
        assertThat(course.getRatingCount()).isEqualTo(0);
    }

    private void seedCourseAndEnrollments(List<Long> studentIds) {
        CourseEntity course = new CourseEntity();
        course.setId(COURSE_ID);
        course.setOwnerTeacherId(OWNER_TEACHER_ID);
        course.setLifecycleStatus("PUBLISHED");
        course.setVersion(0L);
        course.setCreatedBy(OWNER_TEACHER_ID);
        course.setCreatedAt(LocalDateTime.of(2026, 8, 23, 8, 0));
        course.setUpdatedBy(OWNER_TEACHER_ID);
        course.setUpdatedAt(LocalDateTime.of(2026, 8, 23, 8, 0));
        courseMapper.insert(course);

        CourseEnrollmentMapper enrollmentMapper = sqlSessionTemplate.getMapper(CourseEnrollmentMapper.class);
        long id = 1L;
        for (Long studentId : studentIds) {
            CourseEnrollmentEntity enrollment = new CourseEnrollmentEntity();
            enrollment.setId(id++);
            enrollment.setCourseId(COURSE_ID);
            enrollment.setStudentId(studentId);
            enrollment.setSource("FREE");
            enrollment.setStatus("ACTIVE");
            enrollment.setEnrolledAt(LocalDateTime.of(2026, 8, 23, 9, 0));
            enrollment.setVersion(0L);
            enrollmentMapper.insert(enrollment);
        }
    }

    private Long reviewIdOf(Long studentId) {
        return sqlSessionTemplate.getMapper(CourseReviewMapper.class)
                .selectOne(new LambdaQueryWrapper<CourseReviewEntity>()
                        .eq(CourseReviewEntity::getCourseId, COURSE_ID)
                        .eq(CourseReviewEntity::getStudentId, studentId))
                .getId();
    }

    private static List<Class<?>> mapperTypes() {
        return List.of(
                CourseMapper.class,
                CourseEnrollmentMapper.class,
                CourseReviewMapper.class);
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
