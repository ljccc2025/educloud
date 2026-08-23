package com.educloud.course.mapper;

import com.educloud.course.testcontainers.TestContainerImages;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * educloud_course Schema 集成测试（MySQL 8.0.36）。
 * 依据：M05 计划任务 1/2 与 2026-08-18-educloud-data-design.md 第 14/17.1 节、
 * 2026-08-23-educloud-course-design.md 第 5.2 节：
 * 迁移脚本以 course_migration（库级权限 + GRANT OPTION，与 init 脚本一致）执行 V000+V001；
 * course_app 仅持有表级授权，验证技术表/业务表存在、唯一键（含列组合）与索引、
 * 唯一约束强制（SQLState 23000 / errorCode 1062），以及 course_app 对业务表与审计表可 INSERT/SELECT（且无库级权限）。
 */
@Testcontainers
class CourseSchemaIT {

    private static final String APP_PASSWORD = "CourseApp_Test_Password_123";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestContainerImages.mysql())
            .withDatabaseName("educloud_course")
            .withUsername("root")
            .withPassword("root-test-password");

    private static String rootUrl;
    private static String appUrl;

    @BeforeAll
    static void applyMigrations() throws Exception {
        rootUrl = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/educloud_course?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        appUrl = rootUrl.replace("root-test-password", APP_PASSWORD);

        Path sqlDir = migrationDirectory();
        try (Connection root = DriverManager.getConnection(rootUrl, "root", "root-test-password")) {
            try (Statement statement = root.createStatement()) {
                // MySQL 8 不会为 GRANT 自动建用户；course_migration 的库级权限与
                // deploy/docker-compose/mysql/init/001-create-databases.sh 保持一致。
                statement.execute("CREATE USER 'course_app'@'%' IDENTIFIED BY '" + APP_PASSWORD + "'");
                statement.execute("CREATE USER 'course_migration'@'%' IDENTIFIED BY 'Migration_Test_Password_123'");
                statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES, "
                        + "CREATE VIEW, SHOW VIEW, TRIGGER ON educloud_course.* TO 'course_migration'@'%' WITH GRANT OPTION");
            }
        }
        try (Connection migration = DriverManager.getConnection(
                rootUrl, "course_migration", "Migration_Test_Password_123")) {
            ScriptUtils.executeSqlScript(
                    migration,
                    new FileSystemResource(sqlDir.resolve("V000__technical_tables.sql").toFile()));
            // V001 尚未编写时（RED 阶段）跳过执行，让下方断言以表缺失失败；
            // 编写后与 V000 一并执行，校验 8 张业务表 DDL。
            Path v001 = sqlDir.resolve("V001__course.sql");
            if (Files.exists(v001)) {
                ScriptUtils.executeSqlScript(migration, new FileSystemResource(v001.toFile()));
            }
        }
    }

    @Test
    void technicalTablesExistWithRequiredKeysAndColumnTypes() throws Exception {
        try (Connection connection = DriverManager.getConnection(rootUrl, "root", "root-test-password");
                Statement statement = connection.createStatement()) {

            for (String table : List.of(
                    "schema_migration_history", "outbox_event", "outbox_sequence",
                    "inbox_event", "audit_event", "idempotency_record")) {
                try (ResultSet rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name = '" + table + "'")) {
                    rows.next();
                    assertThat(rows.getLong(1))
                            .as("table %s must exist after V000", table)
                            .isEqualTo(1);
                }
            }

            assertUniqueIndex(statement, "outbox_event", "uk_outbox_event_id");
            assertUniqueIndex(statement, "inbox_event", "uk_inbox_event_id");
            assertUniqueIndex(statement, "idempotency_record", "uk_idempotency");

            assertColumnType(statement, "audit_event", "actor_type", "varchar", 32);
        }
    }

    @Test
    void inboxUniqueKeyIsEnforced() throws Exception {
        try (Connection connection = DriverManager.getConnection(rootUrl, "root", "root-test-password");
                Statement statement = connection.createStatement()) {

            String insert = "INSERT INTO inbox_event (id, event_id, event_type, source_service, event_version, "
                    + "source_sequence, aggregate_type, aggregate_id, aggregate_version, process_status, received_at) "
                    + "VALUES (%s, 'evt-course-1', 'Course.Created', 'educloud-course', 1, %s, 'COURSE', 1, 1, 'PENDING', NOW(3))";
            statement.executeUpdate(String.format(insert, 1, 100));
            assertThatThrownBy(() -> statement.executeUpdate(String.format(insert, 2, 101)))
                    .as("uk_inbox_event_id must reject duplicate event_id")
                    .isInstanceOfSatisfying(SQLException.class, CourseSchemaIT::assertDuplicateKeyException);
        }
    }

    @Test
    void businessTablesExistWithRequiredKeysAndTypes() throws Exception {
        try (Connection connection = DriverManager.getConnection(rootUrl, "root", "root-test-password");
                Statement statement = connection.createStatement()) {

            for (String table : List.of(
                    "course_category", "course", "course_version", "course_teacher",
                    "course_audit_submission", "course_enrollment",
                    "course_content_readiness_projection", "course_review")) {
                try (ResultSet rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name = '" + table + "'")) {
                    rows.next();
                    assertThat(rows.getLong(1))
                            .as("table %s must exist after V001", table)
                            .isEqualTo(1);
                }
            }

            // 规格 5.2 列类型关键点：状态用 VARCHAR（应用层状态机校验）、价格 DECIMAL(10,2)
            assertColumnType(statement, "course", "lifecycle_status", "varchar", 32);
            assertColumnType(statement, "course_version", "version_status", "varchar", 32);
            assertDecimalColumn(statement, "course_version", "price", 10, 2);
        }
    }

    @Test
    void businessUniqueIndexesExistWithExpectedColumns() throws Exception {
        try (Connection connection = DriverManager.getConnection(rootUrl, "root", "root-test-password");
                Statement statement = connection.createStatement()) {

            // 规格 5.2 全部唯一键：存在性（NON_UNIQUE=0）+ 列组合
            assertUniqueIndex(statement, "course_category", "uk_course_category_slug");
            assertIndexColumns(statement, "course_category", "uk_course_category_slug", "slug");

            assertUniqueIndex(statement, "course_version", "uk_course_version_no");
            assertIndexColumns(statement, "course_version", "uk_course_version_no", "course_id", "version_no");

            assertUniqueIndex(statement, "course_teacher", "uk_course_teacher");
            assertIndexColumns(statement, "course_teacher", "uk_course_teacher", "course_id", "teacher_id");

            assertUniqueIndex(statement, "course_audit_submission", "uk_course_audit_submission_version");
            assertIndexColumns(statement, "course_audit_submission", "uk_course_audit_submission_version", "course_version_id");

            assertUniqueIndex(statement, "course_enrollment", "uk_course_enrollment");
            assertIndexColumns(statement, "course_enrollment", "uk_course_enrollment", "course_id", "student_id");

            assertUniqueIndex(statement, "course_content_readiness_projection", "uk_course_readiness_course");
            assertIndexColumns(statement, "course_content_readiness_projection", "uk_course_readiness_course", "course_id");

            assertUniqueIndex(statement, "course_content_readiness_projection", "uk_course_readiness_event");
            assertIndexColumns(statement, "course_content_readiness_projection", "uk_course_readiness_event", "source_event_id");

            assertUniqueIndex(statement, "course_review", "uk_course_review");
            assertIndexColumns(statement, "course_review", "uk_course_review", "course_id", "student_id");
        }
    }

    @Test
    void businessNonUniqueIndexesExist() throws Exception {
        try (Connection connection = DriverManager.getConnection(rootUrl, "root", "root-test-password");
                Statement statement = connection.createStatement()) {

            // 规格 5.2 非唯一索引（NON_UNIQUE=1）
            assertIndex(statement, "course", "idx_course_owner_status");
            assertIndex(statement, "course", "idx_course_published_at");
            assertIndex(statement, "course_enrollment", "idx_course_enrollment_student_status");
        }
    }

    @Test
    void businessUniqueKeysAreEnforced() throws Exception {
        try (Connection connection = DriverManager.getConnection(rootUrl, "root", "root-test-password");
                Statement statement = connection.createStatement()) {

            String versionInsert = "INSERT INTO course_version (id, course_id, version_no, category_id, title, "
                    + "level, price, currency, version_status, created_by, created_at) "
                    + "VALUES (%s, 100, 1, 10, 'intro', 'BEGINNER', 0.00, 'CNY', 'DRAFT', 1, NOW(3))";
            statement.executeUpdate(String.format(versionInsert, 1));
            assertThatThrownBy(() -> statement.executeUpdate(String.format(versionInsert, 2)))
                    .as("uk_course_version_no must reject duplicate (course_id, version_no)")
                    .isInstanceOfSatisfying(SQLException.class, CourseSchemaIT::assertDuplicateKeyException);

            String enrollmentInsert = "INSERT INTO course_enrollment (id, course_id, student_id, source, status, "
                    + "enrolled_at, version) VALUES (%s, 100, 200, 'FREE', 'ACTIVE', NOW(3), 0)";
            statement.executeUpdate(String.format(enrollmentInsert, 1));
            assertThatThrownBy(() -> statement.executeUpdate(String.format(enrollmentInsert, 2)))
                    .as("uk_course_enrollment must reject duplicate (course_id, student_id)")
                    .isInstanceOfSatisfying(SQLException.class, CourseSchemaIT::assertDuplicateKeyException);

            String reviewInsert = "INSERT INTO course_review (id, course_id, student_id, rating, content, status, "
                    + "created_at, updated_at) VALUES (%s, 100, 200, 5, 'good', 'VISIBLE', NOW(3), NOW(3))";
            statement.executeUpdate(String.format(reviewInsert, 1));
            assertThatThrownBy(() -> statement.executeUpdate(String.format(reviewInsert, 2)))
                    .as("uk_course_review must reject duplicate (course_id, student_id)")
                    .isInstanceOfSatisfying(SQLException.class, CourseSchemaIT::assertDuplicateKeyException);
        }
    }

    @Test
    void categoryTeacherAuditUniqueKeysAreEnforced() throws Exception {
        try (Connection connection = DriverManager.getConnection(rootUrl, "root", "root-test-password");
                Statement statement = connection.createStatement()) {

            String categoryInsert = "INSERT INTO course_category (id, name, slug, sort_order, status, created_at, updated_at) "
                    + "VALUES (%s, '后端开发', 'dup-slug-1', 1, 'VISIBLE', NOW(3), NOW(3))";
            statement.executeUpdate(String.format(categoryInsert, 901));
            assertThatThrownBy(() -> statement.executeUpdate(String.format(categoryInsert, 902)))
                    .as("uk_course_category_slug must reject duplicate slug")
                    .isInstanceOfSatisfying(SQLException.class, CourseSchemaIT::assertDuplicateKeyException);

            String teacherInsert = "INSERT INTO course_teacher (id, course_id, teacher_id, teacher_role, joined_at) "
                    + "VALUES (%s, 100, 400, 'CO_TEACHER', NOW(3))";
            statement.executeUpdate(String.format(teacherInsert, 901));
            assertThatThrownBy(() -> statement.executeUpdate(String.format(teacherInsert, 902)))
                    .as("uk_course_teacher must reject duplicate (course_id, teacher_id)")
                    .isInstanceOfSatisfying(SQLException.class, CourseSchemaIT::assertDuplicateKeyException);

            String submissionInsert = "INSERT INTO course_audit_submission (id, course_id, course_version_id, status, "
                    + "submitted_by, submitted_at) VALUES (%s, 100, 1001, 'PENDING', 1, NOW(3))";
            statement.executeUpdate(String.format(submissionInsert, 901));
            assertThatThrownBy(() -> statement.executeUpdate(String.format(submissionInsert, 902)))
                    .as("uk_course_audit_submission_version must reject duplicate course_version_id")
                    .isInstanceOfSatisfying(SQLException.class, CourseSchemaIT::assertDuplicateKeyException);
        }
    }

    @Test
    void courseAppCanInsertAndSelectGrantedTables() throws Exception {
        try (Connection app = DriverManager.getConnection(appUrl, "course_app", APP_PASSWORD);
                Statement statement = app.createStatement()) {

            // 技术表：outbox SELECT/INSERT
            statement.executeUpdate("INSERT INTO outbox_event (id, event_id, aggregate_type, aggregate_id, event_type, "
                    + "event_version, aggregate_version, payload_json, request_id, occurred_at, source_sequence, publish_status) "
                    + "VALUES (1, 'evt-out-1', 'COURSE', 1, 'Course.Created', 1, 1, '{}', 'req-course-app', NOW(3), 1, 'PENDING')");
            try (ResultSet rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM outbox_event WHERE id = 1")) {
                rows.next();
                assertThat(rows.getLong(1)).isEqualTo(1);
            }

            // 审计表仅 INSERT/SELECT：UPDATE 必须被拒绝
            statement.executeUpdate("INSERT INTO audit_event (id, audit_id, actor_type, actor_id, action, resource_type, "
                    + "result, request_id, occurred_at, retention_class) "
                    + "VALUES (2, 'audit-1', 'SERVICE_BOOTSTRAP_JOB', 'job-1', 'PROBE', 'COURSE', 'SUCCESS', 'req-course-app', NOW(3), 'STANDARD')");
            try (ResultSet rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM audit_event WHERE id = 2")) {
                rows.next();
                assertThat(rows.getLong(1)).isEqualTo(1);
            }
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE audit_event SET result = 'DENIED' WHERE id = 2"))
                    .isInstanceOf(SQLException.class);

            // 业务表表级授权：course_category / course_review INSERT+SELECT（V001 GRANT 生效验证）
            statement.executeUpdate("INSERT INTO course_category (id, name, slug, sort_order, status, created_at, updated_at) "
                    + "VALUES (900, 'App Grant Probe', 'app-grant-probe', 99, 'VISIBLE', NOW(3), NOW(3))");
            try (ResultSet rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM course_category WHERE id = 900 AND slug = 'app-grant-probe'")) {
                rows.next();
                assertThat(rows.getLong(1)).isEqualTo(1);
            }

            statement.executeUpdate("INSERT INTO course_review (id, course_id, student_id, rating, content, status, "
                    + "created_at, updated_at) VALUES (900, 100, 300, 5, 'grant probe', 'VISIBLE', NOW(3), NOW(3))");
            try (ResultSet rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM course_review WHERE id = 900 AND rating = 5")) {
                rows.next();
                assertThat(rows.getLong(1)).isEqualTo(1);
            }

            // 无库级权限：CREATE TABLE 必须被拒绝
            assertThatThrownBy(() -> statement.executeUpdate("CREATE TABLE forbidden_probe (id BIGINT)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private static void assertDuplicateKeyException(SQLException exception) {
        assertThat(exception.getSQLState()).as("duplicate key SQLState").isEqualTo("23000");
        assertThat(exception.getErrorCode()).as("duplicate key errorCode").isEqualTo(1062);
    }

    private static void assertUniqueIndex(Statement statement, String table, String index) throws SQLException {
        assertIndexCount(statement, table, index, 0);
    }

    private static void assertIndex(Statement statement, String table, String index) throws SQLException {
        assertIndexCount(statement, table, index, 1);
    }

    private static void assertIndexCount(Statement statement, String table, String index, int nonUnique)
            throws SQLException {
        // information_schema.statistics 对每个索引列一行：多列索引计数 > 1，故用 >= 1（参照 FileSchemaIT/SessionSchemaIT）。
        try (ResultSet rows = statement.executeQuery(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = '" + table + "' "
                        + "AND index_name = '" + index + "' AND NON_UNIQUE = " + nonUnique)) {
            rows.next();
            assertThat(rows.getLong(1))
                    .as("index %s on %s (NON_UNIQUE=%s) must exist", index, table, nonUnique)
                    .isGreaterThanOrEqualTo(1);
        }
    }

    private static void assertIndexColumns(Statement statement, String table, String index, String... expectedColumns)
            throws SQLException {
        try (ResultSet rows = statement.executeQuery(
                "SELECT COLUMN_NAME FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = '" + table + "' "
                        + "AND index_name = '" + index + "' ORDER BY SEQ_IN_INDEX")) {
            List<String> actual = new ArrayList<>();
            while (rows.next()) {
                actual.add(rows.getString(1));
            }
            assertThat(actual)
                    .as("index %s on %s column composition", index, table)
                    .containsExactly(expectedColumns);
        }
    }

    private static void assertDecimalColumn(Statement statement, String table, String column,
            int expectedPrecision, int expectedScale) throws SQLException {
        try (ResultSet rows = statement.executeQuery(
                "SELECT DATA_TYPE, NUMERIC_PRECISION, NUMERIC_SCALE FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = '" + table + "' AND column_name = '" + column + "'")) {
            assertThat(rows.next()).as("column %s.%s must exist", table, column).isTrue();
            assertThat(rows.getString(1)).as("column %s.%s data type", table, column).isEqualTo("decimal");
            assertThat(rows.getInt(2)).as("column %s.%s precision", table, column).isEqualTo(expectedPrecision);
            assertThat(rows.getInt(3)).as("column %s.%s scale", table, column).isEqualTo(expectedScale);
        }
    }

    private static void assertColumnType(Statement statement, String table, String column,
            String expectedType, long expectedLength) throws SQLException {
        try (ResultSet rows = statement.executeQuery(
                "SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = '" + table + "' AND column_name = '" + column + "'")) {
            assertThat(rows.next()).as("column %s.%s must exist", table, column).isTrue();
            assertThat(rows.getString(1)).isEqualTo(expectedType);
            assertThat(rows.getLong(2)).isEqualTo(expectedLength);
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
