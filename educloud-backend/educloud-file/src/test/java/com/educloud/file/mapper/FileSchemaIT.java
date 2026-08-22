package com.educloud.file.mapper;

import com.educloud.file.testcontainers.TestContainerImages;
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
 * 文件库业务表 Schema 集成测试（MySQL 8.0.36）。
 * 依据：M04 计划任务 1 与 2026-08-22-educloud-file-design.md 第 5 节：
 * 迁移脚本以 file_migration（库级权限 + GRANT OPTION，与 init 脚本一致）按 V000/V001 顺序执行；
 * file_app 仅持有表级授权，验证 4 张表存在、唯一键/索引/列类型，以及业务与审计表 INSERT+SELECT。
 */
@Testcontainers
class FileSchemaIT {

    private static final String APP_PASSWORD = "FileApp_Test_Password_123";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestContainerImages.mysql())
            .withDatabaseName("educloud_file")
            .withUsername("root")
            .withPassword("root-test-password");

    private static String rootUrl;
    private static String appUrl;

    @BeforeAll
    static void applyMigrations() throws Exception {
        rootUrl = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/educloud_file?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        appUrl = rootUrl.replace("root-test-password", APP_PASSWORD).replace("user=root", "user=file_app");

        Path sqlDir = migrationDirectory();
        try (Connection root = DriverManager.getConnection(rootUrl, "root", "root-test-password")) {
            try (Statement statement = root.createStatement()) {
                // MySQL 8 不会为 GRANT 自动建用户；file_migration 的库级权限与
                // deploy/docker-compose/mysql/init/001-create-databases.sh 保持一致。
                statement.execute("CREATE USER 'file_app'@'%' IDENTIFIED BY '" + APP_PASSWORD + "'");
                statement.execute("CREATE USER 'file_migration'@'%' IDENTIFIED BY 'Migration_Test_Password_123'");
                statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES, "
                        + "CREATE VIEW, SHOW VIEW, TRIGGER ON educloud_file.* TO 'file_migration'@'%' WITH GRANT OPTION");
            }
        }
        try (Connection migration = DriverManager.getConnection(
                rootUrl, "file_migration", "Migration_Test_Password_123")) {
            for (String script : List.of(
                    "V000__technical_tables.sql",
                    "V001__file.sql")) {
                ScriptUtils.executeSqlScript(
                        migration,
                        new FileSystemResource(sqlDir.resolve(script).toFile()));
            }
        }
    }

    @Test
    void fileTablesExistWithRequiredKeysAndColumnTypes() throws Exception {
        try (Connection connection = DriverManager.getConnection(rootUrl, "root", "root-test-password");
                Statement statement = connection.createStatement()) {

            for (String table : List.of(
                    "file_upload_session", "file_object", "file_binding", "file_access_audit")) {
                try (ResultSet rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name = '" + table + "'")) {
                    rows.next();
                    assertThat(rows.getLong(1))
                            .as("table %s must exist after V001", table)
                            .isEqualTo(1);
                }
            }

            assertUniqueIndex(statement, "file_upload_session", "uk_upload_session_object_key");
            assertUniqueIndex(statement, "file_object", "uk_file_object_key");
            assertUniqueIndex(statement, "file_binding", "uk_file_binding");
            assertIndex(statement, "file_object", "idx_file_object_sha256_status");
            assertIndex(statement, "file_binding", "idx_file_binding_owner");
            assertIndex(statement, "file_access_audit", "idx_file_access_audit_file");

            assertColumnType(statement, "file_object", "sha256", "char", 64);
            assertColumnType(statement, "file_binding", "owner_id", "varchar", 128);
            assertColumnType(statement, "file_access_audit", "request_id", "varchar", 36);
            assertDateTimePrecision(statement, "file_upload_session", "put_url_expires_at", 6);
        }
    }

    @Test
    void uniqueKeysAreEnforced() throws Exception {
        try (Connection connection = DriverManager.getConnection(rootUrl, "root", "root-test-password");
                Statement statement = connection.createStatement()) {

            String sessionInsert = "INSERT INTO file_upload_session (id, uploader_id, object_key, bucket, original_name, "
                    + "content_type, status, put_url_expires_at, expires_at, created_at, version) "
                    + "VALUES (%s, 1001, 'upload/obj-key-1', 'educloud-files', 'a.txt', 'text/plain', 'PENDING', "
                    + "NOW(6), DATE_ADD(NOW(6), INTERVAL 15 MINUTE), NOW(6), 0)";
            statement.executeUpdate(String.format(sessionInsert, 1));
            assertThatThrownBy(() -> statement.executeUpdate(String.format(sessionInsert, 2)))
                    .isInstanceOf(SQLException.class);

            String objectInsert = "INSERT INTO file_object (id, object_key, original_name, content_type, size_bytes, "
                    + "sha256, bucket, status, uploader_id, uploaded_at, version) "
                    + "VALUES (%s, 'obj/object-key-1', 'b.txt', 'text/plain', 10, '%s', 'educloud-files', "
                    + "'AVAILABLE', 1001, NOW(6), 0)";
            String sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
            statement.executeUpdate(String.format(objectInsert, 11, sha256));
            assertThatThrownBy(() -> statement.executeUpdate(String.format(objectInsert, 12, sha256)))
                    .isInstanceOf(SQLException.class);

            String bindingInsert = "INSERT INTO file_binding (id, file_id, owner_service, owner_type, owner_id, bound_at) "
                    + "VALUES (%s, 11, 'educloud-user', 'USER_AVATAR', 'u-1001', NOW(6))";
            statement.executeUpdate(String.format(bindingInsert, 21));
            assertThatThrownBy(() -> statement.executeUpdate(String.format(bindingInsert, 22)))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void fileAppCanInsertAndSelectBusinessAndAuditTables() throws Exception {
        try (Connection app = DriverManager.getConnection(appUrl, "file_app", APP_PASSWORD);
                Statement statement = app.createStatement()) {

            statement.executeUpdate("INSERT INTO file_upload_session (id, uploader_id, object_key, bucket, original_name, "
                    + "content_type, status, put_url_expires_at, expires_at, created_at, version) "
                    + "VALUES (101, 2001, 'upload/obj-key-2', 'educloud-files', 'c.txt', 'text/plain', 'PENDING', "
                    + "NOW(6), DATE_ADD(NOW(6), INTERVAL 15 MINUTE), NOW(6), 0)");
            statement.executeUpdate("INSERT INTO file_access_audit (id, file_id, user_id, action, result, request_id, occurred_at) "
                    + "VALUES (201, 11, 2001, 'GRANT_SINGLE', 'SUCCESS', 'req-file-app', NOW(6))");

            try (ResultSet rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM file_upload_session WHERE id = 101")) {
                rows.next();
                assertThat(rows.getLong(1)).isEqualTo(1);
            }
            try (ResultSet rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM file_access_audit WHERE id = 201")) {
                rows.next();
                assertThat(rows.getLong(1)).isEqualTo(1);
            }

            // 审计表仅 INSERT/SELECT：UPDATE 必须被拒绝
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE file_access_audit SET result = 'DENIED' WHERE id = 201"))
                    .isInstanceOf(SQLException.class);

            // 无库级权限：CREATE TABLE 必须被拒绝
            assertThatThrownBy(() -> statement.executeUpdate("CREATE TABLE forbidden_probe (id BIGINT)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private static void assertUniqueIndex(Statement statement, String table, String index) throws SQLException {
        assertIndexCount(statement, table, index, 0);
    }

    private static void assertIndex(Statement statement, String table, String index) throws SQLException {
        assertIndexCount(statement, table, index, 1);
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

    private static void assertDateTimePrecision(Statement statement, String table, String column, int precision)
            throws SQLException {
        try (ResultSet rows = statement.executeQuery(
                "SELECT DATETIME_PRECISION FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = '" + table + "' AND column_name = '" + column + "'")) {
            assertThat(rows.next()).as("column %s.%s must exist", table, column).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(precision);
        }
    }

    private static Path migrationDirectory() {
        // failsafe 工作目录为模块目录 educloud-file；仓库 deploy/sql/file 位于 ../../deploy/sql/file。
        Path candidate = Path.of(System.getProperty("user.dir"), "..", "..", "deploy", "sql", "file");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        throw new IllegalStateException(
                "migration directory not found at " + candidate.toAbsolutePath()
                        + "; run failsafe from the educloud-file module directory");
    }
}
