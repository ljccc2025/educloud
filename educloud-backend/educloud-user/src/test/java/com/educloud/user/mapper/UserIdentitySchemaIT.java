package com.educloud.user.mapper;

import com.educloud.user.testcontainers.TestContainerImages;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 身份与 RBAC 表数据集成测试（MySQL 8.0.36）。
 * 依据：M03 计划任务 2 与数据设计第 3 节；迁移文件按空库顺序执行。
 */
@Testcontainers
class UserIdentitySchemaIT {

    private static final String APP_PASSWORD = "UserApp_Test_Password_123";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestContainerImages.mysql())
            .withDatabaseName("educloud_user")
            .withUsername("root")
            .withPassword("root-test-password");

    private static String rootUrl;
    private static String appUrl;

    @BeforeAll
    static void applyMigrations() throws Exception {
        rootUrl = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/educloud_user?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        appUrl = rootUrl.replace("root-test-password", APP_PASSWORD).replace("user=root", "user=user_app");

        Path sqlDir = migrationDirectory();
        try (Connection root = DriverManager.getConnection(rootUrl, "root", "root-test-password")) {
            try (Statement statement = root.createStatement()) {
                statement.execute("CREATE USER 'user_app'@'%' IDENTIFIED BY '" + APP_PASSWORD + "'");
                statement.execute("CREATE USER 'user_migration'@'%' IDENTIFIED BY 'Migration_Test_Password_123'");
            }
            for (String script : List.of(
                    "V000__technical_tables.sql",
                    "V001__user_identity_and_rbac.sql",
                    "V002__session_and_platform.sql")) {
                ScriptUtils.executeSqlScript(
                        root,
                        new org.springframework.core.io.FileSystemResource(
                                sqlDir.resolve(script).toFile()));
            }
        }
    }

    @AfterAll
    static void stop() {
        // Testcontainers 自动停止容器。
    }

    @Test
    void identityUniquenessAndSeedDataAreEnforced() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                rootUrl, "root", "root-test-password");
                Statement statement = connection.createStatement()) {

            // 唯一索引：username/email/phone
            statement.executeUpdate("INSERT INTO sys_user (id, username, email, phone, password_hash, user_type, status, created_at, updated_at) "
                    + "VALUES (1001, 'alice', 'alice@example.com', '13800000001', 'hash', 'STUDENT', 'ACTIVE', NOW(3), NOW(3))");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO sys_user (id, username, email, phone, password_hash, user_type, status, created_at, updated_at) "
                            + "VALUES (1002, 'alice', 'other@example.com', NULL, 'hash', 'STUDENT', 'ACTIVE', NOW(3), NOW(3))"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO sys_user (id, username, email, phone, password_hash, user_type, status, created_at, updated_at) "
                            + "VALUES (1003, 'bob', 'alice@example.com', NULL, 'hash', 'STUDENT', 'ACTIVE', NOW(3), NOW(3))"))
                    .isInstanceOf(SQLException.class);

            // 内置角色与权限 seed
            ResultSet roles = statement.executeQuery("SELECT COUNT(*) FROM sys_role WHERE built_in = 1");
            roles.next();
            assertThat(roles.getInt(1)).isEqualTo(7);
            ResultSet permissions = statement.executeQuery("SELECT COUNT(*) FROM sys_permission");
            permissions.next();
            assertThat(permissions.getInt(1)).isGreaterThanOrEqualTo(9);
        }
    }

    @Test
    void applicationAccountHasTableScopedGrantsOnly() throws Exception {
        try (Connection app = DriverManager.getConnection(
                appUrl, "user_app", APP_PASSWORD);
                Statement statement = app.createStatement()) {

            // 业务表可写
            statement.executeUpdate("UPDATE sys_permission SET description = description WHERE code = 'user:read'");

            // 审计表仅 INSERT/SELECT：UPDATE 必须被拒绝
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE audit_event SET result = 'x' WHERE audit_id = 'none'"))
                    .isInstanceOf(SQLException.class);

            // 无库级权限：CREATE TABLE 必须被拒绝
            assertThatThrownBy(() -> statement.executeUpdate("CREATE TABLE forbidden_probe (id BIGINT)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private static Path migrationDirectory() {
        // failsafe 工作目录为模块目录 educloud-user；仓库 deploy/sql/user 位于 ../../deploy/sql/user。
        Path candidate = Path.of(System.getProperty("user.dir"), "..", "..", "deploy", "sql", "user");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        throw new IllegalStateException(
                "migration directory not found at " + candidate.toAbsolutePath()
                        + "; run failsafe from the educloud-user module directory");
    }
}
