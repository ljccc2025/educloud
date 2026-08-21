package com.educloud.user.mapper;

import com.educloud.user.testcontainers.TestContainerImages;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 迁移校验机制集成测试（真实 MySQL 8.0.36）。
 * 依据：M03 计划任务 16 与数据设计第 17 节「迁移规则 / 迁移历史与并发保护」：
 * 空库按序升级、重复执行保护（checksum 一致跳过）、已发布脚本 checksum 篡改拒绝、
 * schema_migration_history 记录完整、FAILED 状态必须先审计处理。
 * VM/CI 上以 -Pintegration 执行。测试共享同一容器状态，按 @Order 顺序执行：
 * 先全量应用，再验证幂等/篡改/FAILED 语义（避免 JUnit 默认无序导致 history 表未就绪）。
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MigrationVerificationIT {

    private static final List<String> SCRIPTS = List.of(
            "V000__technical_tables.sql",
            "V001__user_identity_and_rbac.sql",
            "V002__session_and_platform.sql");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestContainerImages.mysql())
            .withDatabaseName("educloud_user")
            .withUsername("root")
            .withPassword("root-test-password");

    private static Path sqlDir;
    private static String url;

    @BeforeAll
    static void locateMigrations() throws Exception {
        Path candidate = Path.of(System.getProperty("user.dir"), "..", "..", "deploy", "sql", "user");
        if (!Files.isDirectory(candidate)) {
            throw new IllegalStateException("migration directory not found at " + candidate.toAbsolutePath());
        }
        sqlDir = candidate;
        url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/educloud_user?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (Connection root = connect(); Statement statement = root.createStatement()) {
            // V000 内含 GRANT 给 user_app/user_migration：MySQL 8 不会为 GRANT 自动建用户，须先创建（幂等）。
            statement.execute("CREATE USER IF NOT EXISTS 'user_app'@'%' IDENTIFIED BY 'UserApp_Test_Password_123'");
            statement.execute("CREATE USER IF NOT EXISTS 'user_migration'@'%' IDENTIFIED BY 'Migration_Test_Password_123'");
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(url, "root", "root-test-password");
    }

    private static String sha256(Path script) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(Files.readAllBytes(script));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /** 模拟 run-migrations.sh 的核心校验语义：已应用（SUCCESS）且 checksum 一致则跳过，否则拒绝。 */
    private static String storedChecksum(Connection connection, String version) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery(
                    "SELECT checksum_sha256 FROM schema_migration_history "
                            + "WHERE version = '" + version + "' AND status = 'SUCCESS'")) {
                return rows.next() ? rows.getString(1) : null;
            }
        } catch (SQLException exception) {
            // 空库：history 表由 V000 引导创建，首个版本应用前该表尚不存在（等价脚本先引导建表）。
            if (exception.getMessage() != null && exception.getMessage().contains("doesn't exist")) {
                return null;
            }
            throw exception;
        }
    }

    /** 对一份脚本：未应用则执行并记录 SUCCESS；已应用但 checksum 不一致则拒绝（等价脚本 fail 语义）。 */
    private static int apply(Connection connection, Path script) throws Exception {
        String scriptName = script.getFileName().toString();
        String version = scriptName.substring(0, scriptName.indexOf('_'));
        String description = scriptName
                .substring(scriptName.indexOf('_') + 1)
                .replace("__", ": ")
                .replace(".sql", "");
        String checksum = sha256(script);
        String stored = storedChecksum(connection, version);
        if (stored != null) {
            if (!stored.equalsIgnoreCase(checksum)) {
                throw new IllegalStateException(
                        "Checksum mismatch for applied migration " + version
                                + " (" + scriptName + "); published migrations must never change");
            }
            return 0;
        }
        ScriptUtils.executeSqlScript(connection, new FileSystemResource(script.toFile()));
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO schema_migration_history "
                            + "(version, description, script_name, checksum_sha256, status, installed_by, installed_at, execution_ms) "
                            + "VALUES ('" + version + "', '" + description + "', '" + scriptName + "', '"
                            + checksum + "', 'SUCCESS', 'it', NOW(3), 1)");
        }
        return 1;
    }

    private static int applyPending(Connection connection, List<String> scripts) throws Exception {
        int applied = 0;
        for (String script : scripts) {
            applied += apply(connection, sqlDir.resolve(script));
        }
        return applied;
    }

    private static long historyRows(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM schema_migration_history")) {
            rows.next();
            return rows.getLong(1);
        }
    }

    @Test
    @Order(1)
    void migrationsApplyInOrderSeedDataSurvivesAndHistoryIsComplete() throws Exception {
        // 空库（或已应用）按序升级：V000、V001 先行，V001 后带代表性数据再升 V002（任务 16 步骤 1）。
        try (Connection connection = connect()) {
            connection.setAutoCommit(true);
            assertThat(applyPending(connection, SCRIPTS.subList(0, 2))).isLessThanOrEqualTo(2);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "INSERT INTO sys_user "
                                + "(id, username, email, phone, password_hash, user_type, status, created_at, updated_at) "
                                + "VALUES (90001, 'seeduser', 'seed@example.com', '13800009001', 'hash', "
                                + "'STUDENT', 'ACTIVE', NOW(3), NOW(3)) "
                                + "ON DUPLICATE KEY UPDATE username = VALUES(username)");
            }
            assertThat(applyPending(connection, SCRIPTS.subList(2, 3))).isLessThanOrEqualTo(1);

            try (Statement statement = connection.createStatement()) {
                // 升级后种子数据保留，且 V002 会话表可用。
                try (ResultSet rows = statement.executeQuery(
                        "SELECT username FROM sys_user WHERE id = 90001")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("seeduser");
                }
                for (String table : List.of("refresh_session", "platform_public_config", "outbox_event", "audit_event")) {
                    try (ResultSet rows = statement.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.tables "
                                    + "WHERE table_schema = DATABASE() AND table_name = '" + table + "'")) {
                        rows.next();
                        assertThat(rows.getLong(1))
                                .as("table %s must exist after V002", table)
                                .isEqualTo(1);
                    }
                }
                // schema_migration_history 记录完整：3 条 SUCCESS、checksum 为 64 位十六进制。
                assertThat(historyRows(connection)).isEqualTo(3);
                try (ResultSet rows = statement.executeQuery(
                        "SELECT version, script_name, checksum_sha256, status, installed_by, installed_at "
                                + "FROM schema_migration_history ORDER BY version")) {
                    int count = 0;
                    while (rows.next()) {
                        count++;
                        assertThat(rows.getString(4)).isEqualTo("SUCCESS");
                        assertThat(rows.getString(3)).matches("[0-9a-f]{64}");
                        assertThat(rows.getString(5)).isNotBlank();
                        assertThat(rows.getTimestamp(6)).isNotNull();
                        assertThat(rows.getString(2)).endsWith(".sql");
                    }
                    assertThat(count).isEqualTo(3);
                }
            }
        }
    }

    @Test
    @Order(2)
    void reapplicationWithUnchangedChecksumsIsANoOp() throws Exception {
        try (Connection connection = connect()) {
            assertThat(applyPending(connection, SCRIPTS)).isZero();
            assertThat(historyRows(connection)).isEqualTo(3);
        }
    }

    @Test
    @Order(3)
    void tamperedChecksumOfAppliedMigrationIsRejected() throws Exception {
        try (Connection connection = connect()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "UPDATE schema_migration_history SET checksum_sha256 = REPEAT('0', 64) "
                                + "WHERE version = 'V002' AND status = 'SUCCESS'");
            }
            try {
                assertThatThrownBy(() -> applyPending(connection, SCRIPTS))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Checksum mismatch");
            } finally {
                // 恢复原始 checksum，避免污染同容器内的其他测试。
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(
                            "UPDATE schema_migration_history h "
                                    + "JOIN (SELECT '" + sha256(sqlDir.resolve("V002__session_and_platform.sql")) + "' AS cs) c "
                                    + "SET h.checksum_sha256 = c.cs WHERE h.version = 'V002'");
                }
            }
        }
    }

    @Test
    @Order(4)
    void failedStatusCannotBeSilentlySuperseded() throws Exception {
        try (Connection connection = connect()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "INSERT INTO schema_migration_history "
                                + "(version, description, script_name, checksum_sha256, status, installed_by, installed_at, execution_ms, error_summary) "
                                + "VALUES ('V999', 'broken', 'V999__broken.sql', REPEAT('f', 64), 'FAILED', 'it', NOW(3), 1, 'boom')");
                assertThatThrownBy(() -> statement.executeUpdate(
                        "INSERT INTO schema_migration_history "
                                + "(version, description, script_name, checksum_sha256, status, installed_by, installed_at, execution_ms) "
                                + "VALUES ('V999', 'broken', 'V999__broken.sql', REPEAT('f', 64), 'SUCCESS', 'it', NOW(3), 1)"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("Duplicate");
            }
        }
    }
}
