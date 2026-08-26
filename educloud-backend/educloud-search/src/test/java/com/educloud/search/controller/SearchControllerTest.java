package com.educloud.search.controller;

import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.web.RequestContextAccessor;
import com.educloud.common.web.RequestIdPolicy;
import com.educloud.common.web.ServletRequestContextAccessor;
import com.educloud.search.config.SecurityConfig;
import com.educloud.search.dto.request.CourseSearchQuery;
import com.educloud.search.dto.response.*;
import com.educloud.search.service.SearchService;
import com.educloud.search.service.SuggestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SearchController.class)
@Import({SecurityConfig.class, SearchControllerTest.TestInfrastructure.class})
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    @MockBean
    private SuggestService suggestService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("匿名用户访问 /api/v1/search/courses 正常放行并返回课程检索结果")
    void testSearchCoursesAnonymousAccess() throws Exception {
        CourseSearchItem item = CourseSearchItem.builder()
                .id("1001")
                .courseId("1001")
                .title("Spring Cloud 微服务实战")
                .subtitle("企业级实战")
                .category("后端开发")
                .priceCents(9900L)
                .isFree(false)
                .score(2.5)
                .build();

        SearchAggregations aggregations = SearchAggregations.builder()
                .categories(List.of(new SearchAggregations.FacetItem("后端开发", 10L)))
                .difficulties(List.of(new SearchAggregations.FacetItem("INTERMEDIATE", 8L)))
                .build();

        CourseSearchResponse response = CourseSearchResponse.builder()
                .items(List.of(item))
                .total(1L)
                .page(1)
                .size(20)
                .isDegraded(false)
                .aggregations(aggregations)
                .build();

        when(searchService.searchCourses(any(CourseSearchQuery.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/search/courses")
                        .param("keyword", "Spring")
                        .param("category", "后端开发")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.isDegraded").value(false))
                .andExpect(jsonPath("$.data.items[0].id").value("1001"))
                .andExpect(jsonPath("$.data.items[0].title").value("Spring Cloud 微服务实战"))
                .andExpect(jsonPath("$.data.aggregations.categories[0].key").value("后端开发"))
                .andExpect(jsonPath("$.data.aggregations.categories[0].count").value(10))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        verify(searchService).searchCourses(any(CourseSearchQuery.class));
    }

    @Test
    @DisplayName("匿名用户访问 /api/v1/search/suggest 正常放行并返回搜索前缀建议补全")
    void testSuggestAnonymousAccess() throws Exception {
        SuggestItem item1 = SuggestItem.builder()
                .text("Spring Boot")
                .highlight("<em>Spring</em> Boot")
                .type("COURSE")
                .score(10.0f)
                .build();
        SuggestItem item2 = SuggestItem.builder()
                .text("Spring Cloud")
                .highlight("<em>Spring</em> Cloud")
                .type("KEYWORD")
                .score(8.0f)
                .build();

        SuggestResponse response = SuggestResponse.of(List.of(item1, item2));
        when(suggestService.suggest(eq("Spr"), eq(5))).thenReturn(response);

        mockMvc.perform(get("/api/v1/search/suggest")
                        .param("q", "Spr")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.suggestions").isArray())
                .andExpect(jsonPath("$.data.suggestions[0].text").value("Spring Boot"))
                .andExpect(jsonPath("$.data.suggestions[0].type").value("COURSE"))
                .andExpect(jsonPath("$.data.suggestions[1].text").value("Spring Cloud"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        verify(suggestService).suggest("Spr", 5);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestInfrastructure {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-26T10:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        RequestIdPolicy requestIdPolicy() {
            return new RequestIdPolicy(UUID::randomUUID);
        }

        @Bean
        RequestContextAccessor requestContextAccessor(RequestIdPolicy requestIdPolicy) {
            return new ServletRequestContextAccessor(requestIdPolicy, null);
        }

        @Bean
        ApiResponseFactory apiResponseFactory(
                RequestContextAccessor requestContextAccessor, Clock clock) {
            return new ApiResponseFactory(requestContextAccessor, clock);
        }
    }
}
