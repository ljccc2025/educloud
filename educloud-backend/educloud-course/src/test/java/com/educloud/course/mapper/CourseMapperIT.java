package com.educloud.course.mapper;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.educloud.course.entity.CourseEntity;
import com.educloud.course.entity.CourseEnrollmentEntity;
import com.educloud.course.testcontainers.TestContainerImages;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M05 任务 4 的 Mapper 集成测试（MySQL 8.0.36 Testcontainer + V000/V001）。
 *
 * <p>以独立 SqlSessionFactory（MyBatis-Plus + 分页/乐观锁拦截器）驱动真实
 * BaseMapper CRUD：course insert→selectById 往返断言（含 DATETIME(3) 毫秒），
 * 乐观锁 update 后 version 由拦截器自动回写递增（测试代码不手动 +1），
 * 携带旧 version 的并发写返回 0 行；course_enrollment 同样验证版本回写。
 *
 * <p>质量审查修订：每个测试用 @BeforeEach 打开 SqlSession、@AfterEach close
 * （未提交事务随 close 回滚），消除单长事务贯穿与测试间数据污染；
 * SqlSessionFactory 注册全部 8 个 Mapper 并有注册/可用性断言。</p>
 */
@Testcontainers
class CourseMapperIT {

    private static final String APP_PASSWORD = "CourseApp_Test_Password_123";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestContainerImages.mysql())
            .withDatabaseName("educloud_course")
            .withUsername("root")
            .withPassword("root-test-password");

    private static SqlSessionFactory sqlSessionFactory;

    private SqlSession sqlSession;
    private CourseMapper courseMapper;
    private CourseEnrollmentMapper enrollmentMapper;

    @BeforeAll
    static void setUp() throws Exception {
        String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/educloud_course?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

        try (Connection root = DriverManager.getConnection(url, "root", "root-test-password");
                Statement statement = root.createStatement()) {
            statement.execute("CREATE USER 'course_app'@'%' IDENTIFIED BY '" + APP_PASSWORD + "'");
            statement.execute("CREATE USER 'course_migration'@'%' IDENTIFIED BY 'Migration_Test_Password_123'");
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES, "
                    + "CREATE VIEW, SHOW VIEW, TRIGGER ON educloud_course.* TO 'course_migration'@'%' WITH GRANT OPTION");
        }
        try (Connection migration = DriverManager.getConnection(
                url, "course_migration", "Migration_Test_Password_123")) {
            ScriptUtils.executeSqlScript(
                    migration,
                    new FileSystemResource(migrationDirectory().resolve("V000__technical_tables.sql").toFile()));
            ScriptUtils.executeSqlScript(
                    migration,
                    new FileSystemResource(migrationDirectory().resolve("V001__course.sql").toFile()));
        }

        PooledDataSource dataSource = new PooledDataSource(
                "com.mysql.cj.jdbc.Driver", url, "course_app", APP_PASSWORD);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        // 必须用 JdbcTransactionFactory：MybatisSqlSessionFactoryBean 默认
        // SpringManagedTransactionFactory 在无 Spring 事务时不动 connection autoCommit
        // （MySQL 默认 true），导致每个语句隐式提交、close/rollback 无法回滚。
        factoryBean.setTransactionFactory(new JdbcTransactionFactory());
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        factoryBean.setConfiguration(configuration);

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        factoryBean.setPlugins(interceptor);

        sqlSessionFactory = factoryBean.getObject();
        // 注册全部 8 个 Mapper：消除"能编译但从未注册"隐患。
        for (Class<?> mapperType : allMapperTypes()) {
            sqlSessionFactory.getConfiguration().addMapper(mapperType);
        }
    }

    @BeforeEach
    void openSession() {
        sqlSession = sqlSessionFactory.openSession();
        courseMapper = sqlSession.getMapper(CourseMapper.class);
        enrollmentMapper = sqlSession.getMapper(CourseEnrollmentMapper.class);
    }

    @AfterEach
    void closeSession() {
        if (sqlSession != null) {
            // PooledDataSource 复用底层连接：显式 rollback(true) 保证未提交事务终止，
            // 避免连接归还连接池后残留事务污染下一个测试（close 本身不可依赖）。
            sqlSession.rollback(true);
            sqlSession.close();
        }
    }

    @Test
    void allEightMappersAreRegisteredAndUsable() {
        for (Class<?> mapperType : allMapperTypes()) {
            assertThat(sqlSession.getMapper(mapperType))
                    .as("%s must be registered on the SqlSessionFactory", mapperType.getSimpleName())
                    .isNotNull();
        }
        // 空表冒烟：CRUD 入口可用且查询可执行（每测试独立 session，关闭即回滚）
        assertThat(courseMapper.selectCount(new QueryWrapper<>())).isZero();
        assertThat(enrollmentMapper.selectCount(new QueryWrapper<>())).isZero();
    }

    @Test
    void courseInsertAndSelectByIdRoundTrip() {
        CourseEntity course = sampleCourse();
        assertThat(courseMapper.insert(course)).isEqualTo(1);
        assertThat(course.getId()).as("ASSIGN_ID must fill snowflake id").isNotNull();

        CourseEntity loaded = courseMapper.selectById(course.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getOwnerTeacherId()).isEqualTo(1001L);
        assertThat(loaded.getLifecycleStatus()).isEqualTo("DRAFT");
        assertThat(loaded.getPublishedVersionId()).isEqualTo(2001L);
        assertThat(loaded.getDraftVersionId()).isEqualTo(2002L);
        assertThat(loaded.getPublishedAt()).isEqualTo(LocalDateTime.of(2026, 8, 23, 10, 0, 0, 123_000_000));
        assertThat(loaded.getRatingAvg()).isEqualByComparingTo("4.50");
        assertThat(loaded.getRatingCount()).isEqualTo(12);
        assertThat(loaded.getEnrollmentCount()).isEqualTo(3);
        assertThat(loaded.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 23, 8, 30, 0, 456_000_000));
        assertThat(loaded.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 23, 8, 30, 0, 456_000_000));
        assertThat(loaded.getVersion()).as("DB default version").isEqualTo(0L);
    }

    @Test
    void optimisticLockIncrementsCourseVersionWithoutManualUpdate() {
        CourseEntity course = sampleCourse();
        courseMapper.insert(course);

        CourseEntity loaded = courseMapper.selectById(course.getId());
        assertThat(loaded.getVersion()).isEqualTo(0L);

        loaded.setLifecycleStatus("PENDING_REVIEW");
        assertThat(courseMapper.updateById(loaded)).isEqualTo(1);
        // 拦截器自动回写新版本：此处未手动 +1。
        assertThat(loaded.getVersion()).isEqualTo(1L);

        CourseEntity reloaded = courseMapper.selectById(course.getId());
        assertThat(reloaded.getLifecycleStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(reloaded.getVersion()).isEqualTo(1L);

        // 模拟并发：携带旧 version 的写必须失败（0 行命中）。
        loaded.setVersion(0L);
        loaded.setLifecycleStatus("PUBLISHED");
        assertThat(courseMapper.updateById(loaded)).isZero();
        assertThat(courseMapper.selectById(course.getId()).getLifecycleStatus())
                .isEqualTo("PENDING_REVIEW");
    }

    @Test
    void optimisticLockIncrementsEnrollmentVersionWithoutManualUpdate() {
        CourseEnrollmentEntity enrollment = sampleEnrollment();
        assertThat(enrollmentMapper.insert(enrollment)).isEqualTo(1);
        assertThat(enrollment.getId()).isNotNull();

        CourseEnrollmentEntity loaded = enrollmentMapper.selectById(enrollment.getId());
        assertThat(loaded.getVersion()).isEqualTo(0L);

        loaded.setStatus("REVOKED");
        loaded.setRevokeReason("manual revoke");
        assertThat(enrollmentMapper.updateById(loaded)).isEqualTo(1);
        // 拦截器自动回写新版本：此处未手动 +1。
        assertThat(loaded.getVersion()).isEqualTo(1L);

        CourseEnrollmentEntity reloaded = enrollmentMapper.selectById(enrollment.getId());
        assertThat(reloaded.getStatus()).isEqualTo("REVOKED");
        assertThat(reloaded.getVersion()).isEqualTo(1L);

        // 模拟并发：携带旧 version 的写必须失败（0 行命中）。
        loaded.setVersion(0L);
        loaded.setStatus("ACTIVE");
        assertThat(enrollmentMapper.updateById(loaded)).isZero();
        assertThat(enrollmentMapper.selectById(enrollment.getId()).getStatus()).isEqualTo("REVOKED");
    }

    private static CourseEntity sampleCourse() {
        CourseEntity course = new CourseEntity();
        course.setOwnerTeacherId(1001L);
        course.setLifecycleStatus("DRAFT");
        course.setPublishedVersionId(2001L);
        course.setDraftVersionId(2002L);
        course.setPublishedAt(LocalDateTime.of(2026, 8, 23, 10, 0, 0, 123_000_000));
        course.setRatingAvg(new BigDecimal("4.50"));
        course.setRatingCount(12);
        course.setEnrollmentCount(3);
        course.setCreatedBy(1001L);
        course.setCreatedAt(LocalDateTime.of(2026, 8, 23, 8, 30, 0, 456_000_000));
        course.setUpdatedBy(1001L);
        course.setUpdatedAt(LocalDateTime.of(2026, 8, 23, 8, 30, 0, 456_000_000));
        return course;
    }

    private static CourseEnrollmentEntity sampleEnrollment() {
        CourseEnrollmentEntity enrollment = new CourseEnrollmentEntity();
        enrollment.setCourseId(100L);
        enrollment.setStudentId(200L);
        enrollment.setSource("FREE");
        enrollment.setStatus("ACTIVE");
        enrollment.setEnrolledAt(LocalDateTime.of(2026, 8, 23, 9, 0, 0, 789_000_000));
        return enrollment;
    }

    private static List<Class<?>> allMapperTypes() {
        return List.of(
                CourseMapper.class,
                CourseVersionMapper.class,
                CourseCategoryMapper.class,
                CourseTeacherMapper.class,
                CourseAuditSubmissionMapper.class,
                CourseEnrollmentMapper.class,
                CourseContentReadinessProjectionMapper.class,
                CourseReviewMapper.class);
    }

    private static Path migrationDirectory() {
        // failsafe 工作目录为模块目录 educloud-course；仓库 deploy/sql/course 位于 ../../deploy/sql/course。
        Path candidate = Path.of(System.getProperty("user.dir"), "..", "..", "deploy", "sql", "course");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        throw new IllegalStateException(
                "migration directory not found at " + candidate.toAbsolutePath()
                        + "; run failsafe from the educloud-course module directory");
    }
}
