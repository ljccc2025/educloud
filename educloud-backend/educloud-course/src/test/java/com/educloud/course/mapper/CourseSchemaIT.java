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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * educloud_course 技术表 Schema 集成测试（MySQL 8.0.36）。
 * 依据：M05 计划任务 1 与 2026-08-18-educloud-data-design.md 第 14/17.1 节：
 * 迁移脚本以 course_migration（库级权限 + GRANT OPTION，与 init 脚本一致）执行 V000；
 * course_app 仅持有表级授权，验证技术表存在、唯一键/列类型，以及 course_app 可 SELECT/INSERT。
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
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void courseAppCanInsertAndSelectTechnicalTables() throws Exception {
        try (Connection app = DriverManager.getConnection(appUrl, "course_app", APP_PASSWORD);
                Statement statement = app.createStatement()) {

            statement.executeUpdate("INSERT INTO outbox_event (id, event_id, aggregate_type, aggregate_id, event_type, "
                    + "event_version, aggregate_version, payload_json, request_id, occurred_at, source_sequence, publish_status) "
                    + "VALUES (1, 'evt-out-1', 'COURSE', 1, 'Course.Created', 1, 1, '{}', 'req-course-app', NOW(3), 1, 'PENDING')");
            statement.executeUpdate("INSERT INTO audit_event (id, audit_id, actor_type, actor_id, action, resource_type, "
                    + "result, request_id, occurred_at, retention_class) "
                    + "VALUES (2, 'audit-1', 'SERVICE_BOOTSTRAP_JOB', 'job-1', 'PROBE', 'COURSE', 'SUCCESS', 'req-course-app', NOW(3), 'STANDARD')");

            try (ResultSet rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM outbox_event WHERE id = 1")) {
                rows.next();
                assertThat(rows.getLong(1)).isEqualTo(1);
            }
            try (ResultSet rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM audit_event WHERE id = 2")) {
                rows.next();
                assertThat(rows.getLong(1)).isEqualTo(1);
            }

            // 审计表仅 INSERT/SELECT：UPDATE 必须被拒绝
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE audit_event SET result = 'DENIED' WHERE id = 2"))
                    .isInstanceOf(SQLException.class);

            // 无库级权限：CREATE TABLE 必须被拒绝
            assertThatThrownBy(() -> statement.executeUpdate("CREATE TABLE forbidden_probe (id BIGINT)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private static void assertUniqueIndex(Statement statement, String table, String index) throws SQLException {
        assertIndexCount(statement, table, index, 0);
    }

    private static void assertIndexCount(Statement statement, String table, String index, int nonUnique)
            throws SQLException {
        // information_schema.statistics 对每个索引列一行：多列索引计数 > 1，故用 >= 1（参照 SessionSchemaIT）。
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
