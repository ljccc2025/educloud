package com.educloud.recommendation.support;

import com.educloud.recommendation.support.CrossDbCourseAccessor.CourseRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CrossDbCourseAccessor 单元测试（JUnit 5 + Mockito，无 Spring 上下文）：
 * 1. 跨库查询失败时各方法独立容错降级（空 list / 空 map），异常不传播到推荐引擎；
 * 2. 查询成功时传入的 RowMapper 能正确映射课程关键字段。
 */
class CrossDbCourseAccessorTest {

    @Test
    @DisplayName("跨库查询失败时返回空集合降级，不抛出异常")
    void queryFailureReturnsEmptyList() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CrossDbCourseAccessor accessor = new CrossDbCourseAccessor(jdbcTemplate);

        // findVisibleCourses 走 query(sql, RowMapper) 重载
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenThrow(new RuntimeException("db down"));
        // findEnrolledCourseContexts / findCoverUrls 走 query(sql, RowMapper, Object...) 重载
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenThrow(new RuntimeException("db down"));
        // findEnrolledCourseIds 走 queryForList(sql, Class, Object...) 重载
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class)))
                .thenThrow(new RuntimeException("db down"));

        assertThat(accessor.findVisibleCourses()).isEmpty();
        assertThat(accessor.findEnrolledCourseIds(1L)).isEmpty();
        assertThat(accessor.findEnrolledCourseContexts(1L)).isEmpty();
        assertThat(accessor.findCoverUrls(Set.of(1L))).isEmpty();
        // 空集合直接返回 emptyMap，不触发任何查询
        assertThat(accessor.findCoverUrls(Set.of())).isEmpty();
    }

    @Test
    @DisplayName("findVisibleCourses 的 RowMapper 正确映射课程关键字段")
    void rowMapperMapsFields() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CrossDbCourseAccessor accessor = new CrossDbCourseAccessor(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<CourseRow> rowMapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("course_id")).thenReturn(100L);
            when(rs.getString("title")).thenReturn("Java 并发编程实战");
            when(rs.getObject("category_id")).thenReturn(5L);
            when(rs.getString("category_name")).thenReturn("编程开发");
            when(rs.getObject("published_at", LocalDateTime.class))
                    .thenReturn(LocalDateTime.of(2026, 8, 1, 10, 30, 0));
            when(rs.getBigDecimal("price")).thenReturn(new BigDecimal("99.00"));
            when(rs.getObject("cover_file_id")).thenReturn(888L);
            when(rs.getObject("enrollment_count")).thenReturn(123);
            when(rs.getBigDecimal("rating_avg")).thenReturn(new BigDecimal("4.50"));
            return List.of(rowMapper.mapRow(rs, 0));
        });

        List<CourseRow> rows = accessor.findVisibleCourses();

        assertThat(rows).hasSize(1);
        CourseRow row = rows.get(0);
        assertThat(row.getCourseId()).isEqualTo(100L);
        assertThat(row.getTitle()).isEqualTo("Java 并发编程实战");
        assertThat(row.getCategoryId()).isEqualTo(5L);
        assertThat(row.getCategoryName()).isEqualTo("编程开发");
        assertThat(row.getPublishedAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 10, 30, 0));
        assertThat(row.getPrice()).isEqualByComparingTo("99.00");
        assertThat(row.getCoverFileId()).isEqualTo(888L);
        assertThat(row.getEnrollmentCount()).isEqualTo(123);
        assertThat(row.getRatingAvg()).isEqualByComparingTo("4.50");
    }

    @Test
    @DisplayName("findCoverUrls 走 3 参 query 重载并拼出 MinIO 直链 URL")
    void findCoverUrlsBuildsPublicUrls() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CrossDbCourseAccessor accessor = new CrossDbCourseAccessor(jdbcTemplate);

        // findCoverUrls 走 query(sql, RowMapper, Object...) 重载，批次大小 1（少于 MAX_IN_IDS）
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<Void> rowMapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getLong("id")).thenReturn(7L);
                    when(rs.getString("bucket")).thenReturn("course-covers");
                    when(rs.getString("object_key")).thenReturn("cover/7.jpg");
                    // 行映射器仅收集 url 到局部 map，返回 null 不参与结果集；此处返回值被调用方丢弃
                    rowMapper.mapRow(rs, 0);
                    return List.of();
                });

        Map<Long, String> urls = accessor.findCoverUrls(Set.of(7L));

        assertThat(urls).containsOnlyKeys(7L);
        assertThat(urls.get(7L)).isEqualTo("http://192.168.100.136:9000/course-covers/cover/7.jpg");
    }

    @Test
    @DisplayName("mapCourseRow 对可空列返回 null 时安全映射，不抛异常")
    void rowMapperHandlesNullColumns() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CrossDbCourseAccessor accessor = new CrossDbCourseAccessor(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<CourseRow> rowMapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("course_id")).thenReturn(100L);
            when(rs.getString("title")).thenReturn("可空列课程");
            // 可空列全部返回 null：getObject 返回 null
            when(rs.getObject("category_id")).thenReturn(null);
            when(rs.getString("category_name")).thenReturn("分类");
            when(rs.getObject("published_at", LocalDateTime.class)).thenReturn(null);
            when(rs.getBigDecimal("price")).thenReturn(null);
            when(rs.getObject("cover_file_id")).thenReturn(null);
            when(rs.getObject("enrollment_count")).thenReturn(null);
            when(rs.getBigDecimal("rating_avg")).thenReturn(null);
            return List.of(rowMapper.mapRow(rs, 0));
        });

        AtomicReference<List<CourseRow>> captured = new AtomicReference<>();
        assertThatCode(() -> captured.set(accessor.findVisibleCourses())).doesNotThrowAnyException();
        List<CourseRow> rows = captured.get();

        assertThat(rows).hasSize(1);
        CourseRow row = rows.get(0);
        assertThat(row.getCourseId()).isEqualTo(100L);
        assertThat(row.getTitle()).isEqualTo("可空列课程");
        assertThat(row.getCategoryId()).isNull();
        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getPrice()).isNull();
        assertThat(row.getCoverFileId()).isNull();
        assertThat(row.getEnrollmentCount()).isNull();
        assertThat(row.getRatingAvg()).isNull();
    }
}
