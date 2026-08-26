package com.educloud.search.support;

import com.educloud.search.document.CourseIndexDoc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseDataExtractorTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private CourseDataExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new CourseDataExtractor(jdbcTemplate);
    }

    @Test
    @DisplayName("测试 countPublishedCourses 正常统计已发布课程总数")
    void testCountPublishedCoursesSuccess() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(12);

        int count = extractor.countPublishedCourses();

        assertThat(count).isEqualTo(12);
        verify(jdbcTemplate, times(1)).queryForObject(anyString(), eq(Integer.class));
    }

    @Test
    @DisplayName("测试 countPublishedCourses 当本库查询失败时自动重试 educloud_course 库前缀")
    void testCountPublishedCoursesFallback() {
        when(jdbcTemplate.queryForObject(contains("FROM course c"), eq(Integer.class)))
                .thenThrow(new BadSqlGrammarException("query", "SELECT ...", new SQLException("Table not found")));
        when(jdbcTemplate.queryForObject(contains("FROM educloud_course.course c"), eq(Integer.class)))
                .thenReturn(8);

        int count = extractor.countPublishedCourses();

        assertThat(count).isEqualTo(8);
        verify(jdbcTemplate, times(2)).queryForObject(anyString(), eq(Integer.class));
    }

    @Test
    @DisplayName("测试 countPublishedCourses 当两次查询均失败时容错返回 0")
    void testCountPublishedCoursesAllFailed() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenThrow(new BadSqlGrammarException("query", "SELECT ...", new SQLException("Table not found")));

        int count = extractor.countPublishedCourses();

        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("测试 extractPublishedCourses 正常抽取课程与嵌套课件并组装 CourseIndexDoc")
    void testExtractPublishedCoursesSuccessWithLessons() throws SQLException {
        // Mock 课程查询
        ResultSet rsCourse = mock(ResultSet.class);
        when(rsCourse.getLong("course_id")).thenReturn(1001L);
        when(rsCourse.getLong("owner_teacher_id")).thenReturn(2001L);
        when(rsCourse.getString("title")).thenReturn("Spring Cloud 实战");
        when(rsCourse.getString("subtitle")).thenReturn("微服务架构");
        when(rsCourse.getString("description")).thenReturn("深度解析分布式系统");
        when(rsCourse.getString("level")).thenReturn("ADVANCED");
        when(rsCourse.getBigDecimal("price")).thenReturn(new BigDecimal("199.00"));
        when(rsCourse.getString("category_name")).thenReturn("后端开发");
        when(rsCourse.getString("category_slug")).thenReturn("BACKEND");
        when(rsCourse.getBigDecimal("rating_avg")).thenReturn(new BigDecimal("4.9"));
        when(rsCourse.getInt("enrollment_count")).thenReturn(350);
        when(rsCourse.getLong("version")).thenReturn(3L);
        when(rsCourse.getTimestamp("published_at")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 8, 20, 10, 0, 0)));
        when(rsCourse.getTimestamp("updated_at")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 8, 25, 12, 0, 0)));

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(10), eq(0)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(rsCourse, 0));
                });

        // Mock 课件查询
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            ResultSet rsLesson = mock(ResultSet.class);
            when(rsLesson.getLong("course_id")).thenReturn(1001L);
            when(rsLesson.getLong("lesson_id")).thenReturn(5001L);
            when(rsLesson.getString("lesson_title")).thenReturn("第1讲 架构总览");
            when(rsLesson.getString("chapter_title")).thenReturn("第一章 概论");
            when(rsLesson.getInt("free_preview")).thenReturn(1);
            handler.processRow(rsLesson);
            return null;
        }).when(jdbcTemplate).query(contains("courseware"), any(RowCallbackHandler.class), any(Object[].class));

        List<CourseIndexDoc> docs = extractor.extractPublishedCourses(0, 10);

        assertThat(docs).hasSize(1);
        CourseIndexDoc doc = docs.get(0);
        assertThat(doc.getId()).isEqualTo("1001");
        assertThat(doc.getCourseId()).isEqualTo("1001");
        assertThat(doc.getTitle()).isEqualTo("Spring Cloud 实战");
        assertThat(doc.getPriceCents()).isEqualTo(19900L);
        assertThat(doc.getIsFree()).isFalse();
        assertThat(doc.getRating()).isEqualTo(4.9f);
        assertThat(doc.getStudentCount()).isEqualTo(350);
        assertThat(doc.getLessonCount()).isEqualTo(1);
        assertThat(doc.getLessons()).hasSize(1);
        assertThat(doc.getLessons().get(0).getId()).isEqualTo("5001");
        assertThat(doc.getLessons().get(0).getTitle()).isEqualTo("第1讲 架构总览");
        assertThat(doc.getLessons().get(0).getChapterTitle()).isEqualTo("第一章 概论");
        assertThat(doc.getLessons().get(0).getIsPreview()).isTrue();
    }

    @Test
    @DisplayName("测试 extractPublishedCourses 当 limit <= 0 或无数据时返回空列表")
    void testExtractPublishedCoursesEmpty() {
        List<CourseIndexDoc> emptyDocs = extractor.extractPublishedCourses(0, 0);
        assertThat(emptyDocs).isEmpty();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(10), eq(100)))
                .thenReturn(Collections.emptyList());

        List<CourseIndexDoc> noDataDocs = extractor.extractPublishedCourses(100, 10);
        assertThat(noDataDocs).isEmpty();
    }

    @Test
    @DisplayName("测试 extractPublishedCourses 当课件表查询失败时优雅降级，仍生成课件为空的课程文档")
    void testExtractPublishedCoursesLessonQueryFailedGracefully() throws SQLException {
        ResultSet rsCourse = mock(ResultSet.class);
        when(rsCourse.getLong("course_id")).thenReturn(1002L);
        when(rsCourse.getString("title")).thenReturn("Java 编程基础");
        when(rsCourse.getBigDecimal("price")).thenReturn(BigDecimal.ZERO);
        when(rsCourse.getInt("enrollment_count")).thenReturn(50);
        when(rsCourse.getLong("version")).thenReturn(1L);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(5), eq(0)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(rsCourse, 0));
                });

        // 模拟课件查询抛出异常
        doThrow(new BadSqlGrammarException("query", "SELECT ...", new SQLException("Table not found")))
                .when(jdbcTemplate).query(contains("courseware"), any(RowCallbackHandler.class), any(Object[].class));

        List<CourseIndexDoc> docs = extractor.extractPublishedCourses(0, 5);

        assertThat(docs).hasSize(1);
        CourseIndexDoc doc = docs.get(0);
        assertThat(doc.getId()).isEqualTo("1002");
        assertThat(doc.getTitle()).isEqualTo("Java 编程基础");
        assertThat(doc.getIsFree()).isTrue();
        assertThat(doc.getLessonCount()).isEqualTo(0);
        assertThat(doc.getLessons()).isEmpty();
    }
}
