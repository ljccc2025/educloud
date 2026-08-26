package com.educloud.search.service;

import com.educloud.search.dto.request.CourseSearchQuery;
import com.educloud.search.dto.response.CourseSearchItem;
import com.educloud.search.dto.response.CourseSearchResponse;
import com.educloud.search.service.fallback.DatabaseFallbackSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseFallbackSearchServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DatabaseFallbackSearchService fallbackSearchService;

    @BeforeEach
    void setUp() {
        fallbackSearchService = new DatabaseFallbackSearchService(jdbcTemplate);
    }

    @Test
    @DisplayName("测试数据库降级模糊检索成功并返回 isDegraded=true")
    void testFallbackSearchSuccess() {
        CourseSearchQuery query = CourseSearchQuery.builder()
                .keyword("微服务")
                .difficulty("INTERMEDIATE")
                .isFree(false)
                .minPriceCents(1000L)
                .maxPriceCents(50000L)
                .sortBy("popular")
                .page(1)
                .size(10)
                .build();

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);

        CourseSearchItem item = CourseSearchItem.builder()
                .id("1001")
                .courseId("1001")
                .title("Spring Cloud 微服务实战")
                .priceCents(19900L)
                .isFree(false)
                .difficulty("INTERMEDIATE")
                .studentCount(120)
                .rating(4.8f)
                .status("PUBLISHED")
                .publishedAt(LocalDateTime.now())
                .build();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(item));

        CourseSearchResponse response = fallbackSearchService.fallbackSearch(query);

        assertThat(response).isNotNull();
        assertThat(response.getIsDegraded()).isTrue();
        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getTitle()).isEqualTo("Spring Cloud 微服务实战");
    }

    @Test
    @DisplayName("测试当数据库未查到结果时返回空结果且 isDegraded=true")
    void testFallbackSearchEmptyResult() {
        CourseSearchQuery query = CourseSearchQuery.builder()
                .keyword("不存在的课程")
                .page(1)
                .size(20)
                .build();

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);

        CourseSearchResponse response = fallbackSearchService.fallbackSearch(query);

        assertThat(response).isNotNull();
        assertThat(response.getIsDegraded()).isTrue();
        assertThat(response.getTotal()).isEqualTo(0L);
        assertThat(response.getItems()).isEmpty();
    }

    @Test
    @DisplayName("测试数据库异常时安全捕获并返回兜底空响应")
    void testFallbackSearchDatabaseException() {
        CourseSearchQuery query = CourseSearchQuery.builder()
                .keyword("Java")
                .build();

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenThrow(new CannotAcquireLockException("DB Lock error"));

        CourseSearchResponse response = fallbackSearchService.fallbackSearch(query);

        assertThat(response).isNotNull();
        assertThat(response.getIsDegraded()).isTrue();
        assertThat(response.getTotal()).isEqualTo(0L);
        assertThat(response.getItems()).isEmpty();
    }
}
