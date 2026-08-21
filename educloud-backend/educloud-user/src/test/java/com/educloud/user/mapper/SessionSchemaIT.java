package com.educloud.user.mapper;

import com.educloud.user.testcontainers.TestContainerImages;
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
 * 会话/服务客户端/平台配置表数据集成测试（MySQL 8.0.36）。
 * 依据：M03 计划任务 3 与数据设计第 3 节（refresh_session/service_client/service_client_credential/
 * platform_public_config/login_audit 的唯一约束、索引与 seed）。
 */
@Testcontainers
class SessionSchemaIT {

    private static final String APP_PASSWORD = "UserApp_Test_Password_123";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestContainerImages.mysql())
            .withDatabaseName("educloud_user")
            .withUsername("root")
            .withPassword("root-test-password");

    private static String rootUrl;

    @BeforeAll
    static void applyMigrations() throws Exception {
        rootUrl = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/educloud_user?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        Path sqlDir = Path.of(System.getProperty("user.dir"), "..", "..", "deploy", "sql", "user");
        if (!Files.isDirectory(sqlDir)) {
            throw new IllegalStateException("migration directory not found at " + sqlDir.toAbsolutePath());
        }
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
                        new FileSystemResource(sqlDir.resolve(script).toFile()));
            }
        }
    }

    @Test
    void sessionAndClientConstraintsAreEnforced() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                rootUrl, "root", "root-test-password");
                Statement statement = connection.createStatement()) {

            String baseInsert = "INSERT INTO refresh_session (id, family_id, token_id, parent_token_id, user_id, session_token_hash, status, client_type, client_fingerprint_hash, issued_at, expires_at) "
                    + "VALUES (%s, 'fam-1', '%s', NULL, 1001, '%s', 'ACTIVE', 'WEB', 'fp', NOW(3), DATE_ADD(NOW(3), INTERVAL 7 DAY))";

            // token_id 与 session_token_hash 唯一
            statement.executeUpdate(String.format(baseInsert, 2001, "tok-1", "aa"));
            assertThatThrownBy(() -> statement.executeUpdate(String.format(baseInsert, 2002, "tok-1", "bb")))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate(String.format(baseInsert, 2003, "tok-2", "aa")))
                    .isInstanceOf(SQLException.class);

            // service_client client_id 唯一；credential (service_client_id, credential_version) 唯一
            statement.executeUpdate("INSERT INTO service_client (id, client_id, status, allowed_audiences_json, allowed_scopes_json, created_at, updated_at) "
                    + "VALUES (3001, 'svc-probe', 'ACTIVE', '[]', '[]', NOW(3), NOW(3))");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO service_client (id, client_id, status, allowed_audiences_json, allowed_scopes_json, created_at, updated_at) "
                            + "VALUES (3002, 'svc-probe', 'ACTIVE', '[]', '[]', NOW(3), NOW(3))"))
                    .isInstanceOf(SQLException.class);
            statement.executeUpdate("INSERT INTO service_client_credential (id, service_client_id, credential_version, secret_hash, status, not_before) "
                    + "VALUES (4001, 3001, 1, 'h1', 'ACTIVE', NOW(3))");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO service_client_credential (id, service_client_id, credential_version, secret_hash, status, not_before) "
                            + "VALUES (4002, 3001, 1, 'h2', 'ACTIVE', NOW(3))"))
                    .isInstanceOf(SQLException.class);

            // 平台公开配置 config_key 唯一与 seed
            ResultSet seeds = statement.executeQuery(
                    "SELECT COUNT(*) FROM platform_public_config WHERE config_key IN ('site_name', 'site_logo_url', 'icp_record')");
            seeds.next();
            assertThat(seeds.getInt(1)).isEqualTo(3);
            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO platform_public_config (id, config_key, config_value, value_type, version, created_at, updated_at) "
                            + "VALUES (5001, 'site_name', 'x', 'STRING', 0, NOW(3), NOW(3))"))
                    .isInstanceOf(SQLException.class);

            // 登录审计可写、索引存在
            statement.executeUpdate("INSERT INTO login_audit (id, login_name_masked, result, request_id, occurred_at) "
                    + "VALUES (6001, 'a***@example.com', 'SUCCESS', 'req-1', NOW(3))");
            ResultSet indexed = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'educloud_user' AND table_name = 'refresh_session' AND index_name = 'idx_refresh_user_expires'");
            indexed.next();
            assertThat(indexed.getInt(1)).isGreaterThanOrEqualTo(1);
        }
    }
}
