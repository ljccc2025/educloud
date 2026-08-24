package com.educloud.content.integration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.educloud.common.id.IdentifierGenerator;
import com.educloud.content.dto.request.ProgressReportRequest;
import com.educloud.content.dto.response.CourseProgressResponse;
import com.educloud.content.entity.ContentRevisionEntity;
import com.educloud.content.entity.CourseContentEntity;
import com.educloud.content.entity.CoursewareEntity;
import com.educloud.content.mapper.ChapterMapper;
import com.educloud.content.mapper.ContentAuditSubmissionMapper;
import com.educloud.content.mapper.ContentRevisionMapper;
import com.educloud.content.mapper.CourseContentMapper;
import com.educloud.content.mapper.CoursewareMapper;
import com.educloud.content.mapper.OutboxEventMapper;
import com.educloud.content.mapper.OutboxSequenceMapper;
import com.educloud.content.mapper.UserCourseProgressMapper;
import com.educloud.content.mapper.UserCoursewareProgressMapper;
import com.educloud.content.service.CourseProgressService;
import com.educloud.content.testcontainers.TestContainerImages;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class LearningProgressIT {

    private static final String APP_PASSWORD = "ContentApp_Test_Password_123";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestContainerImages.mysql())
            .withDatabaseName("educloud_content")
            .withUsername("root")
            .withPassword("root-test-password");

    private static CourseProgressService progressService;
    private static CourseContentMapper contentMapper;
    private static ContentRevisionMapper revisionMapper;
    private static CoursewareMapper coursewareMapper;

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
        configuration.addMapper(UserCourseProgressMapper.class);
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
        revisionMapper = sqlSessionTemplate.getMapper(ContentRevisionMapper.class);
        coursewareMapper = sqlSessionTemplate.getMapper(CoursewareMapper.class);
        UserCoursewareProgressMapper coursewareProgressMapper = sqlSessionTemplate.getMapper(UserCoursewareProgressMapper.class);
        UserCourseProgressMapper courseProgressMapper = sqlSessionTemplate.getMapper(UserCourseProgressMapper.class);

        AtomicLong idSeq = new AtomicLong(2000000000000000000L);
        IdentifierGenerator idGenerator = idSeq::incrementAndGet;

        progressService = new CourseProgressService(
                coursewareProgressMapper,
                courseProgressMapper,
                coursewareMapper,
                contentMapper,
                idGenerator);
    }

    @Test
    void testProgressReportAndAggregation() {
        Long courseId = 999001L;
        Long revisionId = 999002L;
        Long coursewareId1 = 999003L;
        Long coursewareId2 = 999004L;
        Long studentId = 2091648316809035778L;

        CourseContentEntity content = new CourseContentEntity();
        content.setId(999000L);
        content.setCourseId(courseId);
        content.setPublishedRevisionId(revisionId);
        content.setAggregateVersion(1L);
        content.setCreatedAt(LocalDateTime.now());
        content.setUpdatedAt(LocalDateTime.now());
        contentMapper.insert(content);

        ContentRevisionEntity rev = new ContentRevisionEntity();
        rev.setId(revisionId);
        rev.setCourseContentId(content.getId());
        rev.setCourseId(courseId);
        rev.setRevisionNo(1);
        rev.setRevisionStatus("PUBLISHED");
        rev.setCreatedBy(9000000000000000001L);
        rev.setCreatedAt(LocalDateTime.now());
        rev.setPublishedAt(LocalDateTime.now());
        revisionMapper.insert(rev);

        CoursewareEntity cw1 = new CoursewareEntity();
        cw1.setId(coursewareId1);
        cw1.setContentRevisionId(revisionId);
        cw1.setChapterId(999005L);
        cw1.setCourseId(courseId);
        cw1.setTitle("1.1 视频");
        cw1.setCoursewareType("VIDEO");
        cw1.setDurationSeconds(100);
        cw1.setStatus("ACTIVE");
        cw1.setCreatedAt(LocalDateTime.now());
        cw1.setUpdatedAt(LocalDateTime.now());
        coursewareMapper.insert(cw1);

        CoursewareEntity cw2 = new CoursewareEntity();
        cw2.setId(coursewareId2);
        cw2.setContentRevisionId(revisionId);
        cw2.setChapterId(999005L);
        cw2.setCourseId(courseId);
        cw2.setTitle("1.2 文档");
        cw2.setCoursewareType("DOCUMENT");
        cw2.setDurationSeconds(0);
        cw2.setStatus("ACTIVE");
        cw2.setCreatedAt(LocalDateTime.now());
        cw2.setUpdatedAt(LocalDateTime.now());
        coursewareMapper.insert(cw2);

        // Student reports progress for cw1
        ProgressReportRequest req1 = new ProgressReportRequest();
        req1.setPositionSeconds(100);
        req1.setWatchedDeltaSeconds(15);
        req1.setCompleted(true);

        CourseProgressResponse resp1 = progressService.reportProgress(coursewareId1, req1, studentId);
        assertThat(resp1.getProgressPercent()).isEqualTo(50);
        assertThat(resp1.getCompletedCoursewareCount()).isEqualTo(1);
        assertThat(resp1.getTotalCoursewareCount()).isEqualTo(2);

        // Student reports progress for cw2
        ProgressReportRequest req2 = new ProgressReportRequest();
        req2.setPositionSeconds(0);
        req2.setWatchedDeltaSeconds(10);
        req2.setCompleted(true);

        CourseProgressResponse resp2 = progressService.reportProgress(coursewareId2, req2, studentId);
        assertThat(resp2.getProgressPercent()).isEqualTo(100);
        assertThat(resp2.getCompletedCoursewareCount()).isEqualTo(2);
        assertThat(resp2.getTotalCoursewareCount()).isEqualTo(2);
    }
}
