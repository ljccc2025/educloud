package com.educloud.user.service;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refresh 行锁轮换并发集成测试（真实 MySQL 8.0.36）。
 * 依据：M03 设计规格第 4.3 节（按 token 哈希行锁，父 ACTIVE→ROTATED 原子迁移；
 * 并发刷新只有一个成功）。VM/CI 上执行。
 */
@Testcontainers
class RefreshSessionRotationConcurrencyIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestContainerImages.mysql())
            .withDatabaseName("educloud_user")
            .withUsername("root")
            .withPassword("root-test-password");

    private static String url;

    @BeforeAll
    static void applyMigrations() throws Exception {
        url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/educloud_user?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        Path sqlDir = Path.of(System.getProperty("user.dir"), "..", "..", "deploy", "sql", "user");
        if (!Files.isDirectory(sqlDir)) {
            throw new IllegalStateException("migration directory not found at " + sqlDir.toAbsolutePath());
        }
        try (Connection root = DriverManager.getConnection(url, "root", "root-test-password");
                Statement statement = root.createStatement()) {
            statement.execute("CREATE USER 'user_app'@'%' IDENTIFIED BY 'UserApp_Test_Password_123'");
            statement.execute("CREATE USER 'user_migration'@'%' IDENTIFIED BY 'Migration_Test_Password_123'");
            for (String script : List.of(
                    "V000__technical_tables.sql",
                    "V001__user_identity_and_rbac.sql",
                    "V002__session_and_platform.sql")) {
                ScriptUtils.executeSqlScript(root, new FileSystemResource(sqlDir.resolve(script).toFile()));
            }
            statement.executeUpdate("INSERT INTO sys_user (id, username, password_hash, user_type, status, created_at, updated_at) "
                    + "VALUES (1001, 'lockuser', 'hash', 'STUDENT', 'ACTIVE', NOW(3), NOW(3))");
            statement.executeUpdate("INSERT INTO refresh_session "
                    + "(id, family_id, token_id, user_id, session_token_hash, status, client_type, client_fingerprint_hash, issued_at, expires_at) "
                    + "VALUES (2001, 'fam-concurrency', 'tok-1', 1001, 'hash-concurrency', 'ACTIVE', 'STUDENT', 'fp', NOW(3), DATE_ADD(NOW(3), INTERVAL 7 DAY))");
        }
    }

    @Test
    void concurrentRefreshOnlyOneRotationSucceeds() throws Exception {
        CountDownLatch firstLocked = new CountDownLatch(1);
        AtomicReference<String> firstStatus = new AtomicReference<>();
        AtomicReference<String> secondStatus = new AtomicReference<>();

        Thread first = new Thread(() -> {
            try (Connection connection = DriverManager.getConnection(url, "root", "root-test-password")) {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.executeQuery("SELECT id FROM refresh_session WHERE session_token_hash = 'hash-concurrency' FOR UPDATE").next();
                    firstLocked.countDown();
                    Thread.sleep(800);
                    statement.executeUpdate("UPDATE refresh_session SET status = 'ROTATED', consumed_at = NOW(3) "
                            + "WHERE id = 2001 AND status = 'ACTIVE'");
                    connection.commit();
                    firstStatus.set("ROTATED");
                }
            } catch (Exception exception) {
                firstStatus.set("ERROR:" + exception.getClass().getSimpleName());
            }
        });

        Thread second = new Thread(() -> {
            try {
                firstLocked.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            try (Connection connection = DriverManager.getConnection(url, "root", "root-test-password")) {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    ResultSet rows = statement.executeQuery(
                            "SELECT status FROM refresh_session WHERE session_token_hash = 'hash-concurrency' FOR UPDATE");
                    if (rows.next()) {
                        secondStatus.set(rows.getString(1));
                    }
                    connection.rollback();
                }
            } catch (SQLException exception) {
                secondStatus.set("ERROR:" + exception.getClass().getSimpleName());
            }
        });

        first.start();
        second.start();
        first.join(TimeUnit.SECONDS.toMillis(15));
        second.join(TimeUnit.SECONDS.toMillis(15));

        assertThat(firstStatus.get()).isEqualTo("ROTATED");
        // 第二个事务在第一个提交后取得锁，只能读到 ROTATED（并发刷新只有一个成功）。
        assertThat(secondStatus.get()).isEqualTo("ROTATED");
    }
}
