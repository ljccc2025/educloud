package com.educloud.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggest;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggestOption;
import co.elastic.clients.elasticsearch.core.search.FieldSuggester;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import com.educloud.search.config.ElasticsearchProperties;
import com.educloud.search.document.CourseIndexDoc;
import com.educloud.search.dto.response.SuggestItem;
import com.educloud.search.dto.response.SuggestResponse;
import com.educloud.search.service.SuggestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 智能搜索建议与前缀自动补全服务实现
 * 支持 Completion Suggester 毫秒级补全，结合 MatchPhrasePrefix 兜底匹配，并具备异常保护能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestServiceImpl implements SuggestService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String SUGGESTER_NAME = "course-suggest";
    private static final String SUGGEST_FIELD = "title.suggest";

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchProperties properties;

    @Override
    public SuggestResponse suggest(String prefix, Integer limit) {
        if (!StringUtils.hasText(prefix)) {
            return SuggestResponse.empty();
        }

        String cleanPrefix = prefix.trim();
        int finalLimit = resolveLimit(limit);

        try {
            List<SuggestItem> items = new ArrayList<>();
            Set<String> collectedCourseIds = new HashSet<>();

            // 1. 尝试使用 Elasticsearch Completion Suggester 快速补全
            FieldSuggester fieldSuggester = FieldSuggester.of(fs -> fs
                    .prefix(cleanPrefix)
                    .completion(c -> c.field(SUGGEST_FIELD).size(finalLimit).skipDuplicates(true))
            );

            SearchResponse<CourseIndexDoc> response = elasticsearchClient.search(s -> s
                            .index(properties.getAliasName())
                            .suggest(sug -> sug.suggesters(SUGGESTER_NAME, fieldSuggester)),
                    CourseIndexDoc.class
            );

            if (response.suggest() != null && response.suggest().containsKey(SUGGESTER_NAME)) {
                List<Suggestion<CourseIndexDoc>> suggestions = response.suggest().get(SUGGESTER_NAME);
                if (!CollectionUtils.isEmpty(suggestions)) {
                    for (Suggestion<CourseIndexDoc> suggestion : suggestions) {
                        if (suggestion.isCompletion()) {
                            CompletionSuggest<CourseIndexDoc> completion = suggestion.completion();
                            if (completion.options() != null) {
                                for (CompletionSuggestOption<CourseIndexDoc> option : completion.options()) {
                                    String text = option.text();
                                    CourseIndexDoc doc = option.source();
                                    String targetId = doc != null ? doc.getCourseId() : null;
                                    String category = doc != null ? doc.getCategory() : null;
                                    Float score = option.score() != null ? option.score().floatValue() : null;

                                    if (targetId != null) {
                                        collectedCourseIds.add(targetId);
                                    }

                                    items.add(SuggestItem.builder()
                                            .text(text)
                                            .highlight(buildPrefixHighlight(text, cleanPrefix))
                                            .category(category)
                                            .type("COURSE")
                                            .targetId(targetId)
                                            .score(score)
                                            .build());
                                }
                            }
                        }
                    }
                }
            }

            // 2. 若 Completion 建议不足，通过 MatchPhrasePrefix 补充课程标题
            if (items.size() < finalLimit) {
                int remaining = finalLimit - items.size();
                SearchResponse<CourseIndexDoc> phraseResponse = elasticsearchClient.search(s -> s
                                .index(properties.getAliasName())
                                .size(remaining)
                                .query(q -> q.bool(b -> b
                                        .filter(f -> f.term(t -> t.field("status").value(STATUS_PUBLISHED)))
                                        .must(m -> m.matchPhrasePrefix(mp -> mp.field("title").query(cleanPrefix)))
                                )),
                        CourseIndexDoc.class
                );

                if (phraseResponse.hits() != null && !CollectionUtils.isEmpty(phraseResponse.hits().hits())) {
                    for (Hit<CourseIndexDoc> hit : phraseResponse.hits().hits()) {
                        CourseIndexDoc doc = hit.source();
                        if (doc != null && !collectedCourseIds.contains(doc.getCourseId())) {
                            collectedCourseIds.add(doc.getCourseId());
                            items.add(SuggestItem.builder()
                                    .text(doc.getTitle())
                                    .highlight(buildPrefixHighlight(doc.getTitle(), cleanPrefix))
                                    .category(doc.getCategory())
                                    .type("COURSE")
                                    .targetId(doc.getCourseId())
                                    .score(hit.score() != null ? hit.score().floatValue() : null)
                                    .build());
                            if (items.size() >= finalLimit) {
                                break;
                            }
                        }
                    }
                }
            }

            return SuggestResponse.of(items);
        } catch (Exception e) {
            log.warn("Suggest query failed for prefix [{}]: {}. Returning empty suggestion response.", cleanPrefix, e.getMessage());
            return SuggestResponse.empty();
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 8;
        }
        return Math.min(limit, 20);
    }

    private String buildPrefixHighlight(String text, String prefix) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(prefix)) {
            return text;
        }
        int idx = text.toLowerCase().indexOf(prefix.toLowerCase());
        if (idx >= 0) {
            int end = idx + prefix.length();
            return text.substring(0, idx) + "<em>" + text.substring(idx, end) + "</em>" + text.substring(end);
        }
        return text;
    }
}
