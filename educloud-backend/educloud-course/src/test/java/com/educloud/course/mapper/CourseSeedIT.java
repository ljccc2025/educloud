package com.educloud.course.mapper;

import com.educloud.course.testcontainers.TestContainerImages;
import org.junit.jupiter.api.BeforeAll;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M05 任务 18：V002 演示种子数据集成测试（Testcontainer MySQL 8.0.36）。
 *
 * <p>以 root 连接执行 V000+V001+V002（ReviewSummaryIT 同款模式，V001 GRANT 由 root 天然满足），
 * 断言种子数量与形态：分类树 9 行（3 顶级 + 6 子分类）、课程 8 门
 * （PUBLISHED×6 / DRAFT×1 / PENDING_REVIEW×1）、版本 8 行、course_teacher 归属 8 行
 * （全部 OWNER = demo_teacher）、选课 3 行（fe_demo_10 2 + demo_student_2 1）、评价 3 行
 * （110 课程 5+4 → avg 4.50 落规格 §5.3 区间 [3.8, 4.9]，111 课程 4 → avg 4.00）；
 * 指针与汇总一致性：published/draft 指针、published_at、cover_file_id 全 NULL、
 * enrollment_count/rating_avg/rating_count 与明细吻合；V002 重放幂等（行数不变）。
 * 本机无 Docker，IT 在 VM 上以 -Pintegration 执行（EDUCLOUD_TEST_MYSQL_IMAGE 华为云镜像）。</p>
 */
@Testcontainers
class CourseSeedIT {

    /** demo_teacher（user 库 seed：9000000000000000001）。 */
    private static final long DEMO_TEACHER_ID = 9000000000000000001L;
    /** fe_demo_10 学生（user 库注册账号）。 */
    private static final long FE_DEMO_10_STUDENT_ID = 2091029641632157697L;

    /** 种子 ID 约定：固定可读 9000000000000000100+ 序列，避免与运行时 Snowflake ID 冲突。 */
    private static final long SEED_ID_MIN = 9000000000000000100L;
    private static final long SEED_ID_MAX_EXCLUSIVE = 9000000000000000200L;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestContainerImages.mysql())
            .withDatabaseName("educloud_course")
            .withUsername("root")
            .withPassword("root-test-password");

    private static final String APP_PASSWORD = "CourseApp_Test_Password_123";

    private static String rootUrl;

    @BeforeAll
    static void applyMigrations() throws Exception {
        rootUrl = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/educloud_course?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

        Path sqlDir = migrationDirectory();
        try (Connection root = DriverManager.getConnection(rootUrl, "root", "root-test-password")) {
            // MySQL 8 中 GRANT 不再自动建用户（error 1410）：先建 course_app，
            // 与 ReviewSummaryIT/CourseSchemaIT 的既有模式一致，再执行含 GRANT 的 V000/V001。
            try (Statement statement = root.createStatement()) {
                statement.execute("CREATE USER IF NOT EXISTS 'course_app'@'%' IDENTIFIED BY '" + APP_PASSWORD + "'");
            }
            for (String script : List.of("V000__technical_tables.sql", "V001__course.sql")) {
                ScriptUtils.executeSqlScript(root, new FileSystemResource(sqlDir.resolve(script).toFile()));
            }
            // V002 尚未编写时（RED 阶段）跳过执行，让下方断言以空表失败；
            // 编写后与 V000/V001 一并执行，验证种子数量与幂等。
            Path v002 = sqlDir.resolve("V002__seed.sql");
            if (Files.exists(v002)) {
                ScriptUtils.executeSqlScript(root, new FileSystemResource(v002.toFile()));
            }
            // V003（课程资料丰富化：简介/大纲/评价/选课）随 V002 一并执行；
            // RED 阶段（V003 尚未编写）跳过，断言以 V002 基础数据失败。
            Path v003 = sqlDir.resolve("V003__course_rich_seed.sql");
            if (Files.exists(v003)) {
                ScriptUtils.executeSqlScript(root, new FileSystemResource(v003.toFile()));
            }
        }
    }

    @Test
    void seedCountsAndTreeShape() throws Exception {
        try (Connection connection = DriverManager.getConnection(rootUrl, "root", "root-test-password");
                Statement statement = connection.createStatement()) {

            assertThat(count(statement, "SELECT COUNT(*) FROM course_category"))
                    .as("category seed rows").isEqualTo(9L);
            assertThat(count(statement, "SELECT COUNT(*) FROM course_category WHERE parent_id IS NULL"))
                    .as("top-level categories").isEqualTo(3L);
            assertThat(count(statement, "SELECT COUNT(*) FROM course_category WHERE parent_id IS NOT NULL"))
                    .as("child categories").isEqualTo(6L);
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM (SELECT DISTINCT slug FROM course_category) t"))
                    .as("unique category slugs").isEqualTo(9L);
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course_category WHERE status <> 'VISIBLE'"))
                    .as("all categories VISIBLE").isZero();

            assertThat(count(statement, "SELECT COUNT(*) FROM course"))
                    .as("course seed rows").isEqualTo(8L);
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course WHERE lifecycle_status = 'PUBLISHED'"))
                    .as("published courses").isEqualTo(6L);
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course WHERE lifecycle_status = 'DRAFT'"))
                    .as("draft courses").isEqualTo(1L);
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course WHERE lifecycle_status = 'PENDING_REVIEW'"))
                    .as("pending review courses").isEqualTo(1L);

            assertThat(count(statement, "SELECT COUNT(*) FROM course_version"))
                    .as("course version seed rows").isEqualTo(8L);
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course_version WHERE version_status = 'PUBLISHED'"))
                    .as("published versions").isEqualTo(6L);
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course_version WHERE version_status = 'DRAFT'"))
                    .as("draft version").isEqualTo(1L);
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course_version WHERE version_status = 'PENDING_REVIEW'"))
                    .as("pending review version").isEqualTo(1L);
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course_version WHERE cover_file_id IS NOT NULL"))
                    .as("cover_file_id must all be NULL in seed")
                    .isZero();

            assertThat(count(statement, "SELECT COUNT(*) FROM course_teacher"))
                    .as("course_teacher seed rows").isEqualTo(8L);
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course_teacher WHERE teacher_role = 'OWNER'"))
                    .as("owner rows").isEqualTo(8L);
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course_teacher WHERE teacher_id = " + DEMO_TEACHER_ID))
                    .as("all courses owned by demo_teacher").isEqualTo(8L);

            assertThat(count(statement, "SELECT COUNT(*) FROM course_audit_submission"))
                    .as("audit submission seed rows").isEqualTo(1L);
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course_audit_submission WHERE status = 'PENDING' "
                            + "AND submitted_by = " + DEMO_TEACHER_ID))
                    .as("pending submission by demo_teacher").isEqualTo(1L);

            assertThat(count(statement, "SELECT COUNT(*) FROM course_enrollment"))
                    .as("enrollment seed rows").isEqualTo(17L);
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course_enrollment "
                            + "WHERE student_id = " + FE_DEMO_10_STUDENT_ID
                            + " AND status = 'ACTIVE' AND source = 'FREE'"))
                    .as("fe_demo_10 active free enrollments").isEqualTo(2L);
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM (SELECT DISTINCT course_id FROM course_enrollment) t"))
                    .as("enrolled on six distinct courses").isEqualTo(6L);

            assertThat(count(statement, "SELECT COUNT(*) FROM course_review"))
                    .as("review seed rows").isEqualTo(17L);
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course_review WHERE status <> 'VISIBLE'"))
                    .as("all reviews VISIBLE").isZero();
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course_review WHERE status = 'VISIBLE' "
                            + "AND student_id = " + FE_DEMO_10_STUDENT_ID))
                    .as("visible reviews by fe_demo_10").isEqualTo(2L);
        }
    }

    @Test
    void seedPointersAggregatesAndIdRange() throws Exception {
        try (Connection connection = DriverManager.getConnection(rootUrl, "root", "root-test-password");
                Statement statement = connection.createStatement()) {

            // 发布指针：6 门 PUBLISHED 均指向 version_no=1 的 PUBLISHED 版本。
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course c JOIN course_version v "
                            + "ON c.published_version_id = v.id "
                            + "WHERE c.lifecycle_status = 'PUBLISHED' "
                            + "AND v.version_status = 'PUBLISHED' AND v.version_no = 1"))
                    .as("published pointer integrity").isEqualTo(6L);
            // 已发布课程不保留草稿指针（approve 语义：draft_version_id 清空）。
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course WHERE lifecycle_status = 'PUBLISHED' "
                            + "AND draft_version_id IS NOT NULL"))
                    .as("published courses must not carry draft pointer").isZero();
            // DRAFT / PENDING_REVIEW 课程：draft_version_id 指向对应状态版本。
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course c JOIN course_version v "
                            + "ON c.draft_version_id = v.id "
                            + "WHERE c.lifecycle_status = 'DRAFT' AND v.version_status = 'DRAFT'"))
                    .as("draft pointer integrity").isEqualTo(1L);
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course c JOIN course_version v "
                            + "ON c.draft_version_id = v.id "
                            + "WHERE c.lifecycle_status = 'PENDING_REVIEW' "
                            + "AND v.version_status = 'PENDING_REVIEW'"))
                    .as("pending review pointer integrity").isEqualTo(1L);
            // 待审版本存在唯一 PENDING 提交。
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course c JOIN course_version v "
                            + "JOIN course_audit_submission s ON s.course_version_id = v.id "
                            + "WHERE c.lifecycle_status = 'PENDING_REVIEW' "
                            + "AND v.version_status = 'PENDING_REVIEW' AND s.status = 'PENDING'"))
                    .as("pending submission ties to pending review version").isEqualTo(1L);

            // published_at：6 门已发布非空，DRAFT/PENDING_REVIEW 为空。
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course WHERE lifecycle_status = 'PUBLISHED' "
                            + "AND published_at IS NULL"))
                    .as("published courses must carry published_at").isZero();
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course WHERE lifecycle_status <> 'PUBLISHED' "
                            + "AND published_at IS NOT NULL"))
                    .as("non-published courses must not carry published_at").isZero();

            // 根乐观锁 version 全部为 0（种子初始值）。
            assertThat(count(statement,
                    "SELECT COUNT(*) FROM course WHERE version <> 0"))
                    .as("course aggregate version starts at 0").isZero();

            // 汇总列与明细吻合：enrollment_count = ACTIVE 选课数、rating 与 VISIBLE 评价一致。
            try (ResultSet rows = statement.executeQuery(
                    "SELECT c.id, c.enrollment_count, c.rating_count, c.rating_avg, "
                            + "(SELECT COUNT(*) FROM course_enrollment e "
                            + "  WHERE e.course_id = c.id AND e.status = 'ACTIVE') AS actual_enrollment, "
                            + "(SELECT COUNT(*) FROM course_review r "
                            + "  WHERE r.course_id = c.id AND r.status = 'VISIBLE') AS actual_reviews, "
                            + "IFNULL((SELECT AVG(r.rating) FROM course_review r "
                            + "  WHERE r.course_id = c.id AND r.status = 'VISIBLE'), 0.00) AS actual_avg "
                            + "FROM course c ORDER BY c.id")) {
                int checked = 0;
                while (rows.next()) {
                    long courseId = rows.getLong("id");
                    assertThat(rows.getLong("enrollment_count"))
                            .as("course %d enrollment_count", courseId)
                            .isEqualTo(rows.getLong("actual_enrollment"));
                    assertThat(rows.getLong("rating_count"))
                            .as("course %d rating_count", courseId)
                            .isEqualTo(rows.getLong("actual_reviews"));
                    BigDecimal storedAvg = rows.getBigDecimal("rating_avg");
                    BigDecimal actualAvg = rows.getBigDecimal("actual_avg");
                    assertThat(storedAvg)
                            .as("course %d rating_avg", courseId)
                            .isEqualByComparingTo(actualAvg);
                    checked++;
                }
                assertThat(checked).as("aggregate consistency checked for all courses").isEqualTo(8);
            }

            // 种子 ID 全部落在固定可读区间，避免与运行时 Snowflake ID 冲突。
            for (String table : List.of("course_category", "course", "course_version",
                    "course_teacher", "course_audit_submission", "course_enrollment", "course_review")) {
                assertThat(count(statement,
                        "SELECT COUNT(*) FROM " + table + " WHERE id < " + SEED_ID_MIN
                                + " OR id >= " + SEED_ID_MAX_EXCLUSIVE))
                        .as("%s ids must stay in seed range", table)
                        .isZero();
            }
        }
    }

    @Test
    void v002IsReplayable() throws Exception {
        Path sqlDir = migrationDirectory();
        try (Connection root = DriverManager.getConnection(rootUrl, "root", "root-test-password")) {
            ScriptUtils.executeSqlScript(root, new FileSystemResource(sqlDir.resolve("V003__course_rich_seed.sql").toFile()));
        }
        try (Connection connection = DriverManager.getConnection(rootUrl, "root", "root-test-password");
                Statement statement = connection.createStatement()) {
            assertThat(count(statement, "SELECT COUNT(*) FROM course_category")).isEqualTo(9L);
            assertThat(count(statement, "SELECT COUNT(*) FROM course")).isEqualTo(8L);
            assertThat(count(statement, "SELECT COUNT(*) FROM course_version")).isEqualTo(8L);
            assertThat(count(statement, "SELECT COUNT(*) FROM course_teacher")).isEqualTo(8L);
            assertThat(count(statement, "SELECT COUNT(*) FROM course_audit_submission")).isEqualTo(1L);
            assertThat(count(statement, "SELECT COUNT(*) FROM course_enrollment")).isEqualTo(17L);
            assertThat(count(statement, "SELECT COUNT(*) FROM course_review")).isEqualTo(17L);
        }
    }

    private static long count(Statement statement, String sql) throws SQLException {
        try (ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
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
