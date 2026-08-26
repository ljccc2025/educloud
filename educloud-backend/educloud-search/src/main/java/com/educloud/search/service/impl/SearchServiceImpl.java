package com.educloud.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.educloud.search.config.ElasticsearchProperties;
import com.educloud.search.document.CourseIndexDoc;
import com.educloud.search.dto.request.CourseSearchQuery;
import com.educloud.search.dto.response.CourseSearchItem;
import com.educloud.search.dto.response.CourseSearchResponse;
import com.educloud.search.dto.response.SearchAggregations;
import com.educloud.search.service.SearchService;
import com.educloud.search.service.fallback.DatabaseFallbackSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 课程全文检索核心服务实现类
 * 支持多字段加权（title^3.0, subtitle^2.0, teacherName^1.5, description^1.0）、
 * 多维过滤、排序、高亮提取、多维聚合与数据库优雅降级。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private static final String HIGHLIGHT_PRE_TAG = "<em class=\"search-highlight\">";
    private static final String HIGHLIGHT_POST_TAG = "</em>";
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchProperties properties;
    private final DatabaseFallbackSearchService fallbackSearchService;

    @Override
    public CourseSearchResponse searchCourses(CourseSearchQuery query) {
        if (query == null) {
            query = new CourseSearchQuery();
        }

        try {
            return doElasticsearchSearch(query);
        } catch (Exception e) {
            log.warn("Elasticsearch query failed: [{}]. Falling back to DatabaseFallbackSearchService...", e.getMessage());
            return fallbackSearchService.fallbackSearch(query);
        }
    }

    private CourseSearchResponse doElasticsearchSearch(CourseSearchQuery query) throws Exception {
        int page = query.getPage();
        int size = query.getSize();
        int from = (page - 1) * size;
        String indexAlias = properties.getAliasName();

        // 1. 构建 BoolQuery 检索与过滤条件
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        // 强制只检索已上架的课程 (status == PUBLISHED)
        boolBuilder.filter(f -> f.term(t -> t.field("status").value(STATUS_PUBLISHED)));

        // 关键词全文匹配（加权：title^3.0, subtitle^2.0, teacherName^1.5, description^1.0）
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            boolBuilder.must(m -> m.multiMatch(mm -> mm
                    .query(kw)
                    .fields("title^3.0", "subtitle^2.0", "teacherName^1.5", "description^1.0")
                    .type(TextQueryType.BestFields)
            ));
        }

        // 分类过滤（支持分类名或分类编码）
        if (StringUtils.hasText(query.getCategory())) {
            String cat = query.getCategory().trim();
            boolBuilder.filter(f -> f.bool(b -> b
                    .should(s -> s.term(t -> t.field("category").value(cat)))
                    .should(s -> s.term(t -> t.field("categoryCode").value(cat)))
            ));
        }

        // 难度等级过滤
        if (StringUtils.hasText(query.getDifficulty())) {
            boolBuilder.filter(f -> f.term(t -> t.field("difficulty").value(query.getDifficulty().trim())));
        }

        // 是否免费过滤
        if (query.getIsFree() != null) {
            boolBuilder.filter(f -> f.term(t -> t.field("isFree").value(query.getIsFree())));
        }

        // 价格区间过滤 (分)
        if (query.getMinPriceCents() != null || query.getMaxPriceCents() != null) {
            boolBuilder.filter(f -> f.range(r -> {
                RangeQuery.Builder builder = r.field("priceCents");
                if (query.getMinPriceCents() != null) {
                    builder.gte(JsonData.of(query.getMinPriceCents()));
                }
                if (query.getMaxPriceCents() != null) {
                    builder.lte(JsonData.of(query.getMaxPriceCents()));
                }
                return builder;
            }));
        }

        // 2. 构建排序策略
        List<SortOptions> sortOptions = resolveSortOptions(query.getSortBy(), StringUtils.hasText(query.getKeyword()));

        // 3. 构建高亮配置
        Highlight highlight = Highlight.of(h -> h
                .preTags(HIGHLIGHT_PRE_TAG)
                .postTags(HIGHLIGHT_POST_TAG)
                .fields("title", HighlightField.of(hf -> hf.numberOfFragments(0)))
                .fields("subtitle", HighlightField.of(hf -> hf.numberOfFragments(0)))
                .fields("description", HighlightField.of(hf -> hf.numberOfFragments(3).fragmentSize(150)))
        );

        // 4. 构建多维聚合 (分类、难度、价格区间)
        Aggregation catAgg = Aggregation.of(a -> a.terms(t -> t.field("category").size(20)));
        Aggregation diffAgg = Aggregation.of(a -> a.terms(t -> t.field("difficulty").size(10)));
        Aggregation priceAgg = Aggregation.of(a -> a.range(r -> r.field("priceCents").ranges(
                AggregationRange.of(rg -> rg.key("FREE").to(1.0)),
                AggregationRange.of(rg -> rg.key("PAID_UNDER_100").from(1.0).to(10000.0)),
                AggregationRange.of(rg -> rg.key("PAID_OVER_100").from(10000.0))
        )));

        // 5. 发送 Elasticsearch 检索请求
        SearchResponse<CourseIndexDoc> searchResponse = elasticsearchClient.search(s -> s
                        .index(indexAlias)
                        .from(from)
                        .size(size)
                        .query(boolBuilder.build()._toQuery())
                        .sort(sortOptions)
                        .highlight(highlight)
                        .aggregations("categories", catAgg)
                        .aggregations("difficulties", diffAgg)
                        .aggregations("priceRanges", priceAgg),
                CourseIndexDoc.class
        );

        // 6. 解析命中数据并注入高亮片段
        long total = searchResponse.hits().total() != null ? searchResponse.hits().total().value() : 0L;
        List<CourseSearchItem> items = new ArrayList<>();

        if (searchResponse.hits() != null && !CollectionUtils.isEmpty(searchResponse.hits().hits())) {
            for (Hit<CourseIndexDoc> hit : searchResponse.hits().hits()) {
                CourseIndexDoc doc = hit.source();
                if (doc != null) {
                    CourseSearchItem item = mapDocToItem(doc, hit.score());
                    applyHighlight(item, hit.highlight());
                    items.add(item);
                }
            }
        }

        // 7. 解析聚合数据
        SearchAggregations aggregations = extractAggregations(searchResponse.aggregations());

        return CourseSearchResponse.builder()
                .total(total)
                .page(page)
                .size(size)
                .isDegraded(false)
                .items(items)
                .aggregations(aggregations)
                .build();
    }

    private List<SortOptions> resolveSortOptions(String sortBy, boolean hasKeyword) {
        List<SortOptions> options = new ArrayList<>();
        String mode = (sortBy != null) ? sortBy.toLowerCase() : "relevance";

        switch (mode) {
            case "popular" -> {
                options.add(SortOptions.of(s -> s.field(f -> f.field("studentCount").order(SortOrder.Desc))));
                options.add(SortOptions.of(s -> s.field(f -> f.field("rating").order(SortOrder.Desc))));
                options.add(SortOptions.of(s -> s.field(f -> f.field("publishedAt").order(SortOrder.Desc))));
            }
            case "newest" -> {
                options.add(SortOptions.of(s -> s.field(f -> f.field("publishedAt").order(SortOrder.Desc))));
            }
            case "price_asc" -> {
                options.add(SortOptions.of(s -> s.field(f -> f.field("priceCents").order(SortOrder.Asc))));
                options.add(SortOptions.of(s -> s.field(f -> f.field("publishedAt").order(SortOrder.Desc))));
            }
            case "price_desc" -> {
                options.add(SortOptions.of(s -> s.field(f -> f.field("priceCents").order(SortOrder.Desc))));
                options.add(SortOptions.of(s -> s.field(f -> f.field("publishedAt").order(SortOrder.Desc))));
            }
            case "relevance" -> {
                if (hasKeyword) {
                    options.add(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));
                }
                options.add(SortOptions.of(s -> s.field(f -> f.field("publishedAt").order(SortOrder.Desc))));
            }
            default -> {
                if (hasKeyword) {
                    options.add(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));
                }
                options.add(SortOptions.of(s -> s.field(f -> f.field("publishedAt").order(SortOrder.Desc))));
            }
        }
        return options;
    }

    private CourseSearchItem mapDocToItem(CourseIndexDoc doc, Double score) {
        return CourseSearchItem.builder()
                .id(doc.getId())
                .courseId(doc.getCourseId())
                .title(doc.getTitle())
                .subtitle(doc.getSubtitle())
                .description(doc.getDescription())
                .teacherId(doc.getTeacherId())
                .teacherName(doc.getTeacherName())
                .category(doc.getCategory())
                .categoryCode(doc.getCategoryCode())
                .coverUrl(doc.getCoverUrl())
                .difficulty(doc.getDifficulty())
                .priceCents(doc.getPriceCents())
                .isFree(doc.getIsFree())
                .rating(doc.getRating())
                .studentCount(doc.getStudentCount())
                .lessonCount(doc.getLessonCount())
                .status(doc.getStatus())
                .tags(doc.getTags() != null ? doc.getTags() : Collections.emptyList())
                .publishedAt(doc.getPublishedAt())
                .updatedAt(doc.getUpdatedAt())
                .score(score)
                .build();
    }

    private void applyHighlight(CourseSearchItem item, Map<String, List<String>> highlight) {
        if (highlight == null || highlight.isEmpty()) {
            return;
        }
        if (highlight.containsKey("title") && !CollectionUtils.isEmpty(highlight.get("title"))) {
            item.setTitle(String.join("", highlight.get("title")));
        }
        if (highlight.containsKey("subtitle") && !CollectionUtils.isEmpty(highlight.get("subtitle"))) {
            item.setSubtitle(String.join("", highlight.get("subtitle")));
        }
        if (highlight.containsKey("description") && !CollectionUtils.isEmpty(highlight.get("description"))) {
            item.setDescription(String.join("...", highlight.get("description")));
        }
    }

    private SearchAggregations extractAggregations(Map<String, Aggregate> aggMap) {
        if (aggMap == null || aggMap.isEmpty()) {
            return SearchAggregations.empty();
        }

        List<SearchAggregations.FacetItem> categories = new ArrayList<>();
        List<SearchAggregations.FacetItem> difficulties = new ArrayList<>();
        List<SearchAggregations.FacetItem> priceRanges = new ArrayList<>();

        // 解析分类聚合
        Aggregate catAgg = aggMap.get("categories");
        if (catAgg != null && catAgg.isSterms()) {
            StringTermsAggregate sterms = catAgg.sterms();
            if (sterms.buckets() != null && sterms.buckets().isArray()) {
                for (StringTermsBucket bucket : sterms.buckets().array()) {
                    categories.add(new SearchAggregations.FacetItem(bucket.key().stringValue(), bucket.docCount()));
                }
            }
        }

        // 解析难度聚合
        Aggregate diffAgg = aggMap.get("difficulties");
        if (diffAgg != null && diffAgg.isSterms()) {
            StringTermsAggregate sterms = diffAgg.sterms();
            if (sterms.buckets() != null && sterms.buckets().isArray()) {
                for (StringTermsBucket bucket : sterms.buckets().array()) {
                    difficulties.add(new SearchAggregations.FacetItem(bucket.key().stringValue(), bucket.docCount()));
                }
            }
        }

        // 解析价格区间聚合
        Aggregate priceAgg = aggMap.get("priceRanges");
        if (priceAgg != null && priceAgg.isRange()) {
            RangeAggregate rangeAgg = priceAgg.range();
            if (rangeAgg.buckets() != null && rangeAgg.buckets().isArray()) {
                for (RangeBucket bucket : rangeAgg.buckets().array()) {
                    priceRanges.add(new SearchAggregations.FacetItem(bucket.key(), bucket.docCount()));
                }
            }
        }

        return SearchAggregations.builder()
                .categories(categories)
                .difficulties(difficulties)
                .priceRanges(priceRanges)
                .build();
    }
}
