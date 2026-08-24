package com.educloud.content.integration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.educloud.common.api.PageResponse;
import com.educloud.common.id.IdentifierGenerator;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.content.dto.request.ChapterCreateRequest;
import com.educloud.content.dto.request.CoursewareCreateRequest;
import com.educloud.content.dto.response.ChapterResponse;
import com.educloud.content.dto.response.ContentAuditResponse;
import com.educloud.content.dto.response.ContentDraftResponse;
import com.educloud.content.entity.CourseContentEntity;
import com.educloud.content.entity.OutboxEventEntity;
import com.educloud.content.mapper.ChapterMapper;
import com.educloud.content.mapper.ContentAuditSubmissionMapper;
import com.educloud.content.mapper.ContentRevisionMapper;
import com.educloud.content.mapper.CourseContentMapper;
import com.educloud.content.mapper.CoursewareMapper;
import com.educloud.content.mapper.OutboxEventMapper;
import com.educloud.content.mapper.OutboxSequenceMapper;
import com.educloud.content.mapper.UserCoursewareProgressMapper;
import com.educloud.content.messaging.ContentEventPublisher;
import com.educloud.content.messaging.OutboxWriter;
import com.educloud.content.service.ChapterService;
import com.educloud.content.service.ContentAuditService;
import com.educloud.content.service.ContentRevisionService;
import com.educloud.content.service.CourseContentService;
import com.educloud.content.service.CoursewareService;
import com.educloud.content.testcontainers.TestContainerImages;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ContentLifecycleIT {

    private static final String APP_PASSWORD = "ContentApp_Test_Password_123";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestContainerImages.mysql())
            .withDatabaseName("educloud_content")
            .withUsername("root")
            .withPassword("root-test-password");

    private static CourseContentService courseContentService;
    private static ChapterService chapterService;
    private static CoursewareService coursewareService;
    private static ContentRevisionService revisionService;
    private static ContentAuditService auditService;
    private static OutboxEventMapper outboxEventMapper;
    private static CourseContentMapper contentMapper;

    @BeforeAll
    static void bootstrap() throws Exception {
        String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/educloud_content?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

        try (Connection root = DriverManager.getConnection(url, "root", "root-test-password");
             Statement statement = root.createStatement()) {
            statement.execute("CREATE USER IF NOT EXISTS 'content_app'@'%' IDENTIFIED BY '" + APP_PASSWORD + "'");
            statement.execute("GRANT ALL PRIVILEGES ON educloud_content.* TO 'content_app'@'%'");
            statement.execute("FLUSH PRIVILEGES");
        }

        PooledDataSource dataSource = new PooledDataSource();
        dataSource.setDriver("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername("content_app");
        dataSource.setPassword(APP_PASSWORD);

        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(Path.of("../../deploy/sql/content/V000__technical_tables.sql")));
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(Path.of("../../deploy/sql/content/V001__init_content_schema.sql")));
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(CourseContentMapper.class);
        configuration.addMapper(ContentRevisionMapper.class);
        configuration.addMapper(ChapterMapper.class);
        configuration.addMapper(CoursewareMapper.class);
        configuration.addMapper(UserCoursewareProgressMapper.class);
        configuration.addMapper(ContentAuditSubmissionMapper.class);
        configuration.addMapper(OutboxEventMapper.class);
        configuration.addMapper(OutboxSequenceMapper.class);

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        configuration.addInterceptor(interceptor);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        SqlSessionTemplate sqlSessionTemplate = new SqlSessionTemplate(factoryBean.getObject());

        contentMapper = sqlSessionTemplate.getMapper(CourseContentMapper.class);
        ContentRevisionMapper revisionMapper = sqlSessionTemplate.getMapper(ContentRevisionMapper.class);
        ChapterMapper chapterMapper = sqlSessionTemplate.getMapper(ChapterMapper.class);
        CoursewareMapper coursewareMapper = sqlSessionTemplate.getMapper(CoursewareMapper.class);
        UserCoursewareProgressMapper progressMapper = sqlSessionTemplate.getMapper(UserCoursewareProgressMapper.class);
        ContentAuditSubmissionMapper submissionMapper = sqlSessionTemplate.getMapper(ContentAuditSubmissionMapper.class);
        outboxEventMapper = sqlSessionTemplate.getMapper(OutboxEventMapper.class);
        OutboxSequenceMapper outboxSequenceMapper = sqlSessionTemplate.getMapper(OutboxSequenceMapper.class);

        AtomicLong idSeq = new AtomicLong(1000000000000000000L);
        IdentifierGenerator idGenerator = idSeq::incrementAndGet;
        ObjectMapper objectMapper = new ObjectMapper();
        RequestContextAccessor requestContextAccessor = new ServletRequestContextAccessor(new RequestIdPolicy(UUID::randomUUID), null);

        OutboxWriter outboxWriter = new OutboxWriter(outboxEventMapper, outboxSequenceMapper, idGenerator);
        ContentEventPublisher eventPublisher = new ContentEventPublisher(outboxWriter, objectMapper, requestContextAccessor);

        courseContentService = new CourseContentService(contentMapper, revisionMapper, chapterMapper, coursewareMapper, progressMapper, idGenerator);
        chapterService = new ChapterService(chapterMapper, coursewareMapper, revisionMapper, courseContentService, idGenerator);
        coursewareService = new CoursewareService(coursewareMapper, chapterMapper, revisionMapper, idGenerator);
        revisionService = new ContentRevisionService(revisionMapper, submissionMapper, courseContentService, idGenerator, objectMapper);
        auditService = new ContentAuditService(submissionMapper, revisionMapper, contentMapper, eventPublisher);
    }

    @Test
    void testEndToEndContentLifecycle() {
        Long courseId = 888001L;
        Long teacherId = 9000000000000000001L;
        Long adminId = 9000000000000000002L;

        // 1. Teacher creates draft
        ContentDraftResponse draft = courseContentService.getOrCreateDraft(courseId, teacherId);
        assertThat(draft).isNotNull();
        assertThat(draft.getRevisionStatus()).isEqualTo("DRAFT");
        assertThat(draft.getRevisionNo()).isEqualTo(1);

        // 2. Teacher adds chapter
        ChapterCreateRequest chapterReq = new ChapterCreateRequest();
        chapterReq.setTitle("第 1 章：微服务架构基础");
        chapterReq.setDescription("微服务拆分与服务发现");
        chapterReq.setSortOrder(1);
        ChapterResponse chapter = chapterService.addChapter(courseId, chapterReq, teacherId);
        assertThat(chapter.getId()).isNotNull();

        // 3. Teacher adds coursewares
        CoursewareCreateRequest cwReq1 = new CoursewareCreateRequest();
        cwReq1.setTitle("1.1 什么是微服务");
        cwReq1.setCoursewareType("VIDEO");
        cwReq1.setDurationSeconds(600);
        cwReq1.setFreePreview(true);
        cwReq1.setSortOrder(1);
        coursewareService.addCourseware(chapter.getId(), cwReq1, teacherId);

        CoursewareCreateRequest cwReq2 = new CoursewareCreateRequest();
        cwReq2.setTitle("1.2 架构设计文档");
        cwReq2.setCoursewareType("DOCUMENT");
        cwReq2.setDurationSeconds(0);
        cwReq2.setFreePreview(false);
        cwReq2.setSortOrder(2);
        coursewareService.addCourseware(chapter.getId(), cwReq2, teacherId);

        // 4. Teacher submits review
        revisionService.submitReview(draft.getRevisionId(), teacherId);

        // 5. Admin lists audits and approves
        PageResponse<ContentAuditResponse> audits = auditService.listAudits("PENDING", 1, 10);
        assertThat(audits.items()).isNotEmpty();
        ContentAuditResponse audit = audits.items().stream()
                .filter(a -> a.getContentRevisionId().equals(draft.getRevisionId()))
                .findFirst().orElseThrow();

        auditService.approveAudit(audit.getId(), adminId);

        // 6. Verify published content & chapters
        List<ChapterResponse> publishedChapters = courseContentService.getPublishedChapters(courseId, null);
        assertThat(publishedChapters).hasSize(1);
        assertThat(publishedChapters.get(0).getCoursewares()).hasSize(2);

        // 7. Verify Outbox domain event written
        List<OutboxEventEntity> events = outboxEventMapper.selectList(
                new LambdaQueryWrapper<OutboxEventEntity>()
                        .eq(OutboxEventEntity::getAggregateType, "CourseContent")
                        .eq(OutboxEventEntity::getEventType, "ContentRevisionPublished"));
        assertThat(events).isNotEmpty();
    }
}
