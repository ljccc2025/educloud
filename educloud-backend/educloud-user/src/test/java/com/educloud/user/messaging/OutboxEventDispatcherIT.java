package com.educloud.user.messaging;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.educloud.user.entity.OutboxEventEntity;
import com.educloud.user.mapper.OutboxEventMapper;
import com.educloud.user.testcontainers.TestContainerImages;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Outbox 发布链路集成测试（真实 MySQL 8.0.36 + RabbitMQ 3.13）。
 * 依据：M03 设计规格第 9 节与可靠性设计第 4.1 节（交换机 educloud.events、
 * 路由键 aggregateType:aggregateId、PENDING 小批投递→PUBLISHED、失败指数退避、达阈值 FAILED）。
 * VM/CI 上以 -Pintegration 执行。
 */
@Testcontainers
class OutboxEventDispatcherIT {

    private static final String EXCHANGE = "educloud.events";
    private static final String ROUTING_KEY = "user:90002";
    private static final String QUEUE = "it.outbox.queue";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestContainerImages.mysql())
            .withDatabaseName("educloud_user")
            .withUsername("root")
            .withPassword("root-test-password");

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(TestContainerImages.rabbitmq());

    private static SqlSessionFactory sqlSessionFactory;
    private static RabbitTemplate rabbitTemplate;

    @BeforeAll
    static void bootstrap() throws Exception {
        Path sqlDir = Path.of(System.getProperty("user.dir"), "..", "..", "deploy", "sql", "user");
        if (!Files.isDirectory(sqlDir)) {
            throw new IllegalStateException("migration directory not found at " + sqlDir.toAbsolutePath());
        }
        String mysqlUrl = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/educloud_user?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (Connection root = DriverManager.getConnection(mysqlUrl, "root", "root-test-password");
                Statement statement = root.createStatement()) {
            statement.execute("CREATE USER IF NOT EXISTS 'user_app'@'%' IDENTIFIED BY 'UserApp_Test_Password_123'");
            for (String script : List.of(
                    "V000__technical_tables.sql",
                    "V001__user_identity_and_rbac.sql",
                    "V002__session_and_platform.sql")) {
                ScriptUtils.executeSqlScript(root, new FileSystemResource(sqlDir.resolve(script).toFile()));
            }
        }

        // MyBatis-Plus 非 Spring 装配：PooledDataSource + MybatisConfiguration + 注解 Mapper。
        PooledDataSource dataSource = new PooledDataSource(
                "com.mysql.cj.jdbc.Driver", mysqlUrl, "root", "root-test-password");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("it", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(OutboxEventMapper.class);
        sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);

        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(RABBIT.getHost());
        connectionFactory.setPort(RABBIT.getAmqpPort());
        connectionFactory.setUsername(RABBIT.getAdminUsername());
        connectionFactory.setPassword(RABBIT.getAdminPassword());
        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
        rabbitTemplate.setExchange(EXCHANGE);

        try (com.rabbitmq.client.Connection rabbitConnection = rabbitConnectionFactory().newConnection();
                Channel channel = rabbitConnection.createChannel()) {
            channel.exchangeDeclare(EXCHANGE, "direct", true);
            channel.queueDeclare(QUEUE, false, false, false, null);
            channel.queueBind(QUEUE, EXCHANGE, ROUTING_KEY);
        }
    }

    private static com.rabbitmq.client.ConnectionFactory rabbitConnectionFactory() {
        com.rabbitmq.client.ConnectionFactory factory = new com.rabbitmq.client.ConnectionFactory();
        factory.setHost(RABBIT.getHost());
        factory.setPort(RABBIT.getAmqpPort());
        factory.setUsername(RABBIT.getAdminUsername());
        factory.setPassword(RABBIT.getAdminPassword());
        return factory;
    }

    private static void insertOutboxEvent(
            String eventId, long aggregateVersion, String payload, int attemptCount) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                        + "/educloud_user?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "root", "root-test-password");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO outbox_event "
                            + "(id, event_id, aggregate_type, aggregate_id, event_type, event_version, aggregate_version, "
                            + "payload_json, request_id, trace_id, occurred_at, source_sequence, publish_status, "
                            + "attempt_count, next_attempt_at) "
                            + "VALUES (" + Math.abs(eventId.hashCode() % 1000000) + ", '" + eventId + "', "
                            + "'user', '90002', 'UserRegistered', 1, " + aggregateVersion + ", '"
                            + payload.replace("'", "''") + "', '" + UUID.randomUUID() + "', NULL, NOW(3), "
                            + aggregateVersion + ", 'PENDING', " + attemptCount + ", NULL)");
        }
    }

    private static String outboxStatus(String eventId) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                        + "/educloud_user?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "root", "root-test-password");
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT publish_status, attempt_count, next_attempt_at, published_at "
                                + "FROM outbox_event WHERE event_id = '" + eventId + "'")) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1) + "|" + rows.getInt(2)
                    + "|" + (rows.getTimestamp(3) == null ? "" : "scheduled")
                    + "|" + (rows.getTimestamp(4) == null ? "" : "published");
        }
    }

    @Test
    void pendingEventIsPublishedToRabbitMqAndMarkedPublished() throws Exception {
        String eventId = UUID.randomUUID().toString();
        insertOutboxEvent(eventId, 1, "{\"userId\":90002}", 0);

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            OutboxEventMapper mapper = session.getMapper(OutboxEventMapper.class);
            new OutboxEventDispatcher(mapper, rabbitTemplate, new ObjectMapper()).dispatchPending();
        }

        assertThat(outboxStatus(eventId)).isEqualTo("PUBLISHED|0||published");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            GetResponse response = null;
            try (com.rabbitmq.client.Connection rabbitConnection = rabbitConnectionFactory().newConnection();
                    Channel channel = rabbitConnection.createChannel()) {
                response = channel.basicGet(QUEUE, true);
            }
            assertThat(response).as("message must arrive on educloud.events with key " + ROUTING_KEY).isNotNull();
            String body = new String(response.getBody(), StandardCharsets.UTF_8);
            assertThat(body)
                    .contains("\"eventId\":\"" + eventId + "\"")
                    .contains("\"aggregateType\":\"user\"")
                    .contains("\"aggregateId\":\"90002\"")
                    .contains("\"sourceService\":\"educloud-user\"")
                    .contains("\"data\"");
        });
    }

    @Test
    void poisonedPayloadBacksOffAndHitsRetryLimitAsFailed() throws Exception {
        String backingOff = UUID.randomUUID().toString();
        insertOutboxEvent(backingOff, 2, "not-json", 0);
        String exhausted = UUID.randomUUID().toString();
        insertOutboxEvent(exhausted, 3, "not-json", 9);

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            OutboxEventMapper mapper = session.getMapper(OutboxEventMapper.class);
            new OutboxEventDispatcher(mapper, rabbitTemplate, new ObjectMapper()).dispatchPending();
        }

        // 退避：attempt 0 -> 1，仍 PENDING，且 next_attempt_at 被排程（5s * 1）。
        assertThat(outboxStatus(backingOff)).isEqualTo("PENDING|1|scheduled|");
        // 达阈值：attempt 9 -> 10 >= MAX_ATTEMPTS(10)，标记 FAILED。
        assertThat(outboxStatus(exhausted)).isEqualTo("FAILED|10||");
    }
}
