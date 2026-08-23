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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 课程域 RBAC 迁移集成测试（MySQL 8.0.36，Testcontainer）。
 * 依据：M05 计划任务 3 与 educloud-course 设计规格第 3 节「RBAC 扩展」；
 * 在 V001（角色/权限表）之上执行 V004，断言 9 个 course:* 权限码、
 * COURSE_REVIEWER 内置角色、角色-权限挂载存在，且 V004 可幂等重放。
 */
@Testcontainers
class CoursePermissionMigrationIT {

    private static final String APP_PASSWORD = "UserApp_Test_Password_123";

    private static final List<String> SCRIPTS = List.of(
            "V000__technical_tables.sql",
            "V001__user_identity_and_rbac.sql",
            "V002__session_and_platform.sql",
            "V003__widen_audit_actor_type.sql",
            "V004__course_permissions.sql");

    private static final List<String> COURSE_PERMISSION_CODES = List.of(
            "course:create",
            "course:update",
            "course:submit",
            "course:audit",
            "course:offline",
            "course:republish",
            "course:archive",
            "course:enroll",
            "course:student:read");

    private static final Map<String, String> EXPECTED_ACTIONS = Map.of(
            "course:create", "create",
            "course:update", "update",
            "course:submit", "submit",
            "course:audit", "audit",
            "course:offline", "offline",
            "course:republish", "republish",
            "course:archive", "archive",
            "course:enroll", "enroll",
            "course:student:read", "student:read");

    private static final List<String> TEACHER_PERMISSION_CODES = List.of(
            "course:create",
            "course:update",
            "course:submit",
            "course:offline",
            "course:republish",
            "course:archive",
            "course:enroll",
            "course:student:read");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestContainerImages.mysql())
            .withDatabaseName("educloud_user")
            .withUsername("root")
            .withPassword("root-test-password");

    private static Path sqlDir;
    private static String url;

    @BeforeAll
    static void applyMigrations() throws Exception {
        sqlDir = Path.of(System.getProperty("user.dir"), "..", "..", "deploy", "sql", "user");
        if (!Files.isDirectory(sqlDir)) {
            throw new IllegalStateException(
                    "migration directory not found at " + sqlDir.toAbsolutePath()
                            + "; run failsafe from the educloud-user module directory");
        }
        url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/educloud_user?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (Connection root = DriverManager.getConnection(url, "root", "root-test-password");
                Statement statement = root.createStatement()) {
            // V000 内含 GRANT：MySQL 8 不会为 GRANT 自动建用户，须先创建。
            statement.execute("CREATE USER IF NOT EXISTS 'user_app'@'%' IDENTIFIED BY '" + APP_PASSWORD + "'");
            statement.execute(
                    "CREATE USER IF NOT EXISTS 'user_migration'@'%' IDENTIFIED BY 'Migration_Test_Password_123'");
        }
        try (Connection root = DriverManager.getConnection(url, "root", "root-test-password")) {
            for (String script : SCRIPTS) {
                ScriptUtils.executeSqlScript(root, new FileSystemResource(sqlDir.resolve(script).toFile()));
            }
        }
    }

    @Test
    void coursePermissionCodesAndReviewerRoleAreSeededByV004() throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "root", "root-test-password")) {
            for (String code : COURSE_PERMISSION_CODES) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT resource, action FROM sys_permission WHERE code = ?")) {
                    ps.setString(1, code);
                    try (ResultSet rows = ps.executeQuery()) {
                        assertThat(rows.next()).as("permission %s must exist after V004", code).isTrue();
                        assertThat(rows.getString(1)).as("resource of %s", code).isEqualTo("course");
                        assertThat(rows.getString(2)).as("action of %s", code)
                                .isEqualTo(EXPECTED_ACTIONS.get(code));
                    }
                }
            }

            assertThat(count(connection, "SELECT COUNT(*) FROM sys_role "
                    + "WHERE code = 'COURSE_REVIEWER' AND built_in = 1"))
                    .as("COURSE_REVIEWER built-in role must exist")
                    .isEqualTo(1);

            assertThat(count(connection, "SELECT COUNT(*) FROM sys_role_permission rp "
                    + "JOIN sys_role r ON r.id = rp.role_id "
                    + "JOIN sys_permission p ON p.id = rp.permission_id "
                    + "WHERE r.code = 'COURSE_REVIEWER' AND p.code = 'course:audit'"))
                    .as("COURSE_REVIEWER must be mounted to course:audit")
                    .isEqualTo(1);
        }
    }

    @Test
    void roleMountsMatchPlan() throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "root", "root-test-password")) {
            assertThat(rolePermissionCodes(connection, "COURSE_REVIEWER"))
                    .as("COURSE_REVIEWER mounts")
                    .containsExactlyInAnyOrder("course:audit");
            assertThat(rolePermissionCodes(connection, "STUDENT"))
                    .as("STUDENT mounts")
                    .containsExactlyInAnyOrder("course:enroll");
            assertThat(rolePermissionCodes(connection, "TEACHER"))
                    .as("TEACHER mounts")
                    .containsExactlyInAnyOrderElementsOf(TEACHER_PERMISSION_CODES);
            assertThat(rolePermissionCodes(connection, "SYSTEM_ADMIN"))
                    .as("SYSTEM_ADMIN mounts (ADMIN -> all course permissions)")
                    .containsAll(COURSE_PERMISSION_CODES);
            assertThat(rolePermissionCodes(connection, "SUPER_ADMIN"))
                    .as("SUPER_ADMIN mounts (ADMIN -> all course permissions)")
                    .containsAll(COURSE_PERMISSION_CODES);
        }
    }

    @Test
    void v004ReplayIsIdempotent() throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "root", "root-test-password")) {
            long permissionRows = count(connection, "SELECT COUNT(*) FROM sys_permission");
            long roleRows = count(connection, "SELECT COUNT(*) FROM sys_role");
            long mappingRows = count(connection, "SELECT COUNT(*) FROM sys_role_permission");

            assertThatCode(() -> ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource(sqlDir.resolve("V004__course_permissions.sql").toFile())))
                    .as("replaying V004 must not throw")
                    .doesNotThrowAnyException();

            assertThat(count(connection, "SELECT COUNT(*) FROM sys_permission"))
                    .as("permission rows must be unchanged after replay")
                    .isEqualTo(permissionRows);
            assertThat(count(connection, "SELECT COUNT(*) FROM sys_role"))
                    .as("role rows must be unchanged after replay")
                    .isEqualTo(roleRows);
            assertThat(count(connection, "SELECT COUNT(*) FROM sys_role_permission"))
                    .as("role-permission rows must be unchanged after replay")
                    .isEqualTo(mappingRows);
        }
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static Set<String> rolePermissionCodes(Connection connection, String roleCode) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT p.code FROM sys_role_permission rp "
                        + "JOIN sys_role r ON r.id = rp.role_id "
                        + "JOIN sys_permission p ON p.id = rp.permission_id "
                        + "WHERE r.code = ?")) {
            ps.setString(1, roleCode);
            try (ResultSet rows = ps.executeQuery()) {
                Set<String> codes = new HashSet<>();
                while (rows.next()) {
                    codes.add(rows.getString(1));
                }
                return codes;
            }
        }
    }
}
