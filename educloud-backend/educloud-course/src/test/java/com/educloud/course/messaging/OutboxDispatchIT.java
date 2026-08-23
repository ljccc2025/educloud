package com.educloud.course.messaging;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.educloud.course.entity.OutboxEventEntity;
import com.educloud.course.mapper.OutboxEventMapper;
import com.educloud.course.testcontainers.TestContainerImages;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
 * M05 任务 15：Outbox 分发链路集成测试（真实 MySQL 8.0.36 + RabbitMQ 3.13 Testcontainer）。
 *
 * <p>依据：M03/M04 发布器模式与 M04 坑 3 —— 交换机为 Topic（educloud.events）、routing key
 * 点分隔 aggregateType.aggregateId（Course.10001）、队列以 Course.# 通配绑定接收；
 * PENDING 小批投递→PUBLISHED、失败退避（attempt+1 + next_attempt_at 排程）、达阈值 FAILED。
 * 镜像可由 EDUCLOUD_TEST_RABBITMQ_IMAGE 覆盖（华为云镜像仓库）；VM/CI 上以 -Pintegration 执行。</p>
 */
@Testcontainers
class OutboxDispatchIT {

    private static final String EXCHANGE = "educloud.events";
    private static final String BINDING_PATTERN = "Course.#";
    private static final String ROUTING_KEY = "Course.10001";
    private static final String QUEUE = "it.course.outbox.queue";
    private static final String APP_PASSWORD = "CourseApp_Test_Password_123";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestContainerImages.mysql())
            .withDatabaseName("educloud_course")
            .withUsername("root")
            .withPassword("root-test-password");

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(TestContainerImages.rabbitmq());

    private static SqlSessionFactory sqlSessionFactory;
    private static RabbitTemplate rabbitTemplate;

    @BeforeAll
    static void bootstrap() throws Exception {
        String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/educloud_course?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        Path sqlDir = migrationDirectory();
        try (Connection root = DriverManager.getConnection(url, "root", "root-test-password");
                Statement statement = root.createStatement()) {
            statement.execute("CREATE USER IF NOT EXISTS 'course_app'@'%' IDENTIFIED BY '" + APP_PASSWORD + "'");
            ScriptUtils.executeSqlScript(root, new FileSystemResource(sqlDir.resolve("V000__technical_tables.sql").toFile()));
        }

        // MyBatis-Plus 非 Spring 装配：PooledDataSource + MybatisConfiguration + 注解 Mapper。
        PooledDataSource dataSource = new PooledDataSource(
                "com.mysql.cj.jdbc.Driver", url, "root", "root-test-password");
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

        declareInfrastructure();
    }

    @BeforeEach
    void cleanDatabase() throws Exception {
        String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/educloud_course?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (Connection root = DriverManager.getConnection(url, "root", "root-test-password");
                Statement statement = root.createStatement()) {
            statement.execute("DELETE FROM outbox_event");
            statement.execute("UPDATE outbox_sequence SET `last_value` = 0 WHERE source_name = 'educloud-course'");
        }
    }

    /** 幂等声明 Topic 交换机/临时队列/绑定（供测试自恢复；交换机被外部重建后也能重声明）。 */
    private static void declareInfrastructure() throws Exception {
        try (com.rabbitmq.client.Connection rabbitConnection = rabbitConnectionFactory().newConnection();
                Channel channel = rabbitConnection.createChannel()) {
            channel.exchangeDeclare(EXCHANGE, "topic", true);
            channel.queueDeclare(QUEUE, false, false, false, null);
            channel.queueBind(QUEUE, EXCHANGE, BINDING_PATTERN);
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
        String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/educloud_course?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (Connection connection = DriverManager.getConnection(url, "root", "root-test-password");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO outbox_event "
                            + "(id, event_id, aggregate_type, aggregate_id, event_type, event_version, aggregate_version, "
                            + "payload_json, request_id, trace_id, occurred_at, source_sequence, publish_status, "
                            + "attempt_count, next_attempt_at) "
                            + "VALUES (" + Math.abs(eventId.hashCode() % 1000000) + ", '" + eventId + "', "
                            + "'Course', '10001', 'CoursePublished', 1, " + aggregateVersion + ", '"
                            + payload.replace("'", "''") + "', '" + UUID.randomUUID() + "', NULL, NOW(3), "
                            + aggregateVersion + ", 'PENDING', " + attemptCount + ", NULL)");
        }
    }

    private static String outboxStatus(String eventId) throws Exception {
        String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/educloud_course?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (Connection connection = DriverManager.getConnection(url, "root", "root-test-password");
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
    void pendingCourseEventIsDispatchedToRabbitMqAndMarkedPublished() throws Exception {
        declareInfrastructure();
        String eventId = UUID.randomUUID().toString();
        insertOutboxEvent(eventId, 1, "{\"courseId\":10001}", 0);

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
            assertThat(response).as("message must arrive on " + EXCHANGE + " with key " + ROUTING_KEY).isNotNull();
            String body = new String(response.getBody(), StandardCharsets.UTF_8);
            assertThat(body)
                    .contains("\"eventId\":\"" + eventId + "\"")
                    .contains("\"eventType\":\"CoursePublished\"")
                    .contains("\"aggregateType\":\"Course\"")
                    .contains("\"aggregateId\":\"10001\"")
                    .contains("\"sourceService\":\"educloud-course\"")
                    .contains("\"sourceSequence\":1")
                    .contains("\"data\"");
        });
    }

    @Test
    void unreachableBrokerDeliveryBacksOffAndHitsRetryLimitAsFailed() throws Exception {
        // 注入"投递失败"：连一个不可达地址的 RabbitTemplate（连接失败是同步异常，真实对应 broker 不可用）。
        CachingConnectionFactory deadFactory = new CachingConnectionFactory();
        deadFactory.setHost("127.0.0.1");
        deadFactory.setPort(1);
        deadFactory.setConnectionTimeout(500);
        RabbitTemplate deadTemplate = new RabbitTemplate(deadFactory);
        deadTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
        deadTemplate.setExchange(EXCHANGE);

        String backingOff = UUID.randomUUID().toString();
        insertOutboxEvent(backingOff, 2, "{\"courseId\":10001}", 0);
        String exhausted = UUID.randomUUID().toString();
        insertOutboxEvent(exhausted, 3, "{\"courseId\":10001}", 9);

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            OutboxEventMapper mapper = session.getMapper(OutboxEventMapper.class);
            new OutboxEventDispatcher(mapper, deadTemplate, new ObjectMapper()).dispatchPending();
        }

        // 退避：attempt 0 -> 1，仍 PENDING，且 next_attempt_at 被排程（5s * 1）。
        assertThat(outboxStatus(backingOff)).isEqualTo("PENDING|1|scheduled|");
        // 达阈值：attempt 9 -> 10 >= MAX_ATTEMPTS(10)，标记 FAILED。
        assertThat(outboxStatus(exhausted)).isEqualTo("FAILED|10|scheduled|");
    }

    private static Path migrationDirectory() {
        Path candidate = Path.of(System.getProperty("user.dir"), "..", "..", "deploy", "sql", "course");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        throw new IllegalStateException(
                "migration directory not found at " + candidate.toAbsolutePath()
                        + "; run failsafe from the educloud-course module directory");
    }
}
