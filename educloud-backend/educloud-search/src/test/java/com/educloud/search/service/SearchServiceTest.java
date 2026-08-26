package com.educloud.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import com.educloud.search.config.ElasticsearchProperties;
import com.educloud.search.document.CourseIndexDoc;
import com.educloud.search.dto.request.CourseSearchQuery;
import com.educloud.search.dto.response.CourseSearchItem;
import com.educloud.search.dto.response.CourseSearchResponse;
import com.educloud.search.dto.response.SearchAggregations;
import com.educloud.search.service.fallback.DatabaseFallbackSearchService;
import com.educloud.search.service.impl.SearchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private DatabaseFallbackSearchService fallbackSearchService;

    private ElasticsearchProperties properties;
    private SearchServiceImpl searchService;

    @BeforeEach
    void setUp() {
        properties = new ElasticsearchProperties();
        properties.setAliasName("educloud_course_search");
        searchService = new SearchServiceImpl(elasticsearchClient, properties, fallbackSearchService);
    }

    @Test
    @DisplayName("测试关键词搜索、高亮解析与聚合数据提取")
    void testSearchCoursesWithKeywordAndAggregations() throws Exception {
        CourseSearchQuery query = CourseSearchQuery.builder()
                .keyword("Spring Cloud")
                .category("后端开发")
                .difficulty("INTERMEDIATE")
                .isFree(false)
                .minPriceCents(1000L)
                .maxPriceCents(50000L)
                .sortBy("relevance")
                .page(1)
                .size(10)
                .build();

        SearchResponse<CourseIndexDoc> mockResponse = mock(SearchResponse.class);
        HitsMetadata<CourseIndexDoc> hitsMetadata = mock(HitsMetadata.class);
        TotalHits totalHits = TotalHits.of(th -> th.value(42L).relation(TotalHitsRelation.Eq));
        when(hitsMetadata.total()).thenReturn(totalHits);

        CourseIndexDoc doc = CourseIndexDoc.builder()
                .id("2091648316809035780")
                .courseId("2091648316809035780")
                .title("Spring Cloud 微服务实战开发")
                .subtitle("企业级云原生")
                .description("全面掌握微服务架构")
                .teacherId("9001")
                .teacherName("李明远")
                .category("后端开发")
                .categoryCode("BACKEND")
                .coverUrl("https://example.com/cover.jpg")
                .difficulty("INTERMEDIATE")
                .priceCents(19900L)
                .isFree(false)
                .rating(4.9f)
                .studentCount(1280)
                .lessonCount(24)
                .status("PUBLISHED")
                .tags(List.of("Spring Cloud", "微服务"))
                .publishedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Hit<CourseIndexDoc> hit = mock(Hit.class);
        when(hit.source()).thenReturn(doc);
        when(hit.score()).thenReturn(3.85);
        when(hit.highlight()).thenReturn(Map.of(
                "title", List.of("<em class=\"search-highlight\">Spring Cloud</em> 微服务实战开发"),
                "subtitle", List.of("企业级<em class=\"search-highlight\">云原生</em>"),
                "description", List.of("全面掌握<em class=\"search-highlight\">微服务</em>架构")
        ));
        when(hitsMetadata.hits()).thenReturn(List.of(hit));
        when(mockResponse.hits()).thenReturn(hitsMetadata);

        // 构造聚合 Mock
        Aggregate catAgg = mock(Aggregate.class);
        when(catAgg.isSterms()).thenReturn(true);
        StringTermsAggregate catTerms = mock(StringTermsAggregate.class);
        Buckets<StringTermsBucket> catBuckets = mock(Buckets.class);
        StringTermsBucket catBucket = mock(StringTermsBucket.class);
        when(catBucket.key()).thenReturn(FieldValue.of("后端开发"));
        when(catBucket.docCount()).thenReturn(18L);
        when(catBuckets.isArray()).thenReturn(true);
        when(catBuckets.array()).thenReturn(List.of(catBucket));
        when(catTerms.buckets()).thenReturn(catBuckets);
        when(catAgg.sterms()).thenReturn(catTerms);

        Aggregate diffAgg = mock(Aggregate.class);
        when(diffAgg.isSterms()).thenReturn(true);
        StringTermsAggregate diffTerms = mock(StringTermsAggregate.class);
        Buckets<StringTermsBucket> diffBuckets = mock(Buckets.class);
        StringTermsBucket diffBucket = mock(StringTermsBucket.class);
        when(diffBucket.key()).thenReturn(FieldValue.of("INTERMEDIATE"));
        when(diffBucket.docCount()).thenReturn(22L);
        when(diffBuckets.isArray()).thenReturn(true);
        when(diffBuckets.array()).thenReturn(List.of(diffBucket));
        when(diffTerms.buckets()).thenReturn(diffBuckets);
        when(diffAgg.sterms()).thenReturn(diffTerms);

        Aggregate priceAgg = mock(Aggregate.class);
        when(priceAgg.isRange()).thenReturn(true);
        RangeAggregate rangeAggregate = mock(RangeAggregate.class);
        Buckets<RangeBucket> rangeBuckets = mock(Buckets.class);
        RangeBucket rangeBucket = mock(RangeBucket.class);
        when(rangeBucket.key()).thenReturn("PAID_OVER_100");
        when(rangeBucket.docCount()).thenReturn(20L);
        when(rangeBuckets.isArray()).thenReturn(true);
        when(rangeBuckets.array()).thenReturn(List.of(rangeBucket));
        when(rangeAggregate.buckets()).thenReturn(rangeBuckets);
        when(priceAgg.range()).thenReturn(rangeAggregate);

        Map<String, Aggregate> aggregations = Map.of(
                "categories", catAgg,
                "difficulties", diffAgg,
                "priceRanges", priceAgg
        );
        when(mockResponse.aggregations()).thenReturn(aggregations);

        when(elasticsearchClient.search(any(Function.class), eq(CourseIndexDoc.class)))
                .thenReturn(mockResponse);

        CourseSearchResponse response = searchService.searchCourses(query);

        assertThat(response).isNotNull();
        assertThat(response.getIsDegraded()).isFalse();
        assertThat(response.getTotal()).isEqualTo(42L);
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getItems()).hasSize(1);

        CourseSearchItem item = response.getItems().get(0);
        assertThat(item.getId()).isEqualTo("2091648316809035780");
        assertThat(item.getTitle()).isEqualTo("<em class=\"search-highlight\">Spring Cloud</em> 微服务实战开发");
        assertThat(item.getSubtitle()).isEqualTo("企业级<em class=\"search-highlight\">云原生</em>");
        assertThat(item.getDescription()).isEqualTo("全面掌握<em class=\"search-highlight\">微服务</em>架构");
        assertThat(item.getScore()).isEqualTo(3.85);

        // 验证聚合
        assertThat(response.getAggregations().getCategories()).hasSize(1);
        assertThat(response.getAggregations().getCategories().get(0).getKey()).isEqualTo("后端开发");
        assertThat(response.getAggregations().getCategories().get(0).getCount()).isEqualTo(18L);

        assertThat(response.getAggregations().getDifficulties()).hasSize(1);
        assertThat(response.getAggregations().getDifficulties().get(0).getKey()).isEqualTo("INTERMEDIATE");

        assertThat(response.getAggregations().getPriceRanges()).hasSize(1);
        assertThat(response.getAggregations().getPriceRanges().get(0).getKey()).isEqualTo("PAID_OVER_100");
    }

    @Test
    @DisplayName("测试不同排序策略 (popular, newest, price_asc, price_desc)")
    void testSearchSortingStrategies() throws Exception {
        for (String sortBy : List.of("popular", "newest", "price_asc", "price_desc")) {
            CourseSearchQuery query = CourseSearchQuery.builder()
                    .sortBy(sortBy)
                    .build();

            SearchResponse<CourseIndexDoc> mockResponse = mock(SearchResponse.class);
            HitsMetadata<CourseIndexDoc> hitsMetadata = mock(HitsMetadata.class);
            when(hitsMetadata.total()).thenReturn(TotalHits.of(th -> th.value(0L).relation(TotalHitsRelation.Eq)));
            when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
            when(mockResponse.hits()).thenReturn(hitsMetadata);
            when(mockResponse.aggregations()).thenReturn(Collections.emptyMap());

            when(elasticsearchClient.search(any(Function.class), eq(CourseIndexDoc.class)))
                    .thenReturn(mockResponse);

            CourseSearchResponse response = searchService.searchCourses(query);
            assertThat(response).isNotNull();
            assertThat(response.getIsDegraded()).isFalse();
        }
    }

    @Test
    @DisplayName("测试传入 null Query 时的默认处理")
    void testSearchWithNullQuery() throws Exception {
        SearchResponse<CourseIndexDoc> mockResponse = mock(SearchResponse.class);
        HitsMetadata<CourseIndexDoc> hitsMetadata = mock(HitsMetadata.class);
        when(hitsMetadata.total()).thenReturn(TotalHits.of(th -> th.value(0L).relation(TotalHitsRelation.Eq)));
        when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
        when(mockResponse.hits()).thenReturn(hitsMetadata);
        when(mockResponse.aggregations()).thenReturn(null);

        when(elasticsearchClient.search(any(Function.class), eq(CourseIndexDoc.class)))
                .thenReturn(mockResponse);

        CourseSearchResponse response = searchService.searchCourses(null);
        assertThat(response).isNotNull();
        assertThat(response.getIsDegraded()).isFalse();
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(20);
        assertThat(response.getAggregations().getCategories()).isEmpty();
    }

    @Test
    @DisplayName("测试当 Elasticsearch 抛出异常时自动触发 DatabaseFallbackSearchService 降级")
    void testSearchFallbackWhenElasticsearchFails() throws Exception {
        CourseSearchQuery query = CourseSearchQuery.builder()
                .keyword("Java")
                .page(1)
                .size(10)
                .build();

        when(elasticsearchClient.search(any(Function.class), eq(CourseIndexDoc.class)))
                .thenThrow(new IOException("Elasticsearch cluster unavailable"));

        CourseSearchResponse fallbackResponse = CourseSearchResponse.degraded(
                1L, 1, 10,
                List.of(CourseSearchItem.builder().id("1001").title("Java Fallback Course").build())
        );
        when(fallbackSearchService.fallbackSearch(query)).thenReturn(fallbackResponse);

        CourseSearchResponse response = searchService.searchCourses(query);

        assertThat(response).isNotNull();
        assertThat(response.getIsDegraded()).isTrue();
        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(response.getItems().get(0).getTitle()).isEqualTo("Java Fallback Course");

        verify(fallbackSearchService, times(1)).fallbackSearch(query);
    }
}
