package com.educloud.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.*;
import com.educloud.search.config.ElasticsearchProperties;
import com.educloud.search.document.CourseIndexDoc;
import com.educloud.search.dto.response.SuggestItem;
import com.educloud.search.dto.response.SuggestResponse;
import com.educloud.search.service.impl.SuggestServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuggestServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    private ElasticsearchProperties properties;
    private SuggestServiceImpl suggestService;

    @BeforeEach
    void setUp() {
        properties = new ElasticsearchProperties();
        properties.setAliasName("educloud_course_search");
        suggestService = new SuggestServiceImpl(elasticsearchClient, properties);
    }

    @Test
    @DisplayName("测试基于 Completion Suggester 的智能搜索前缀自动补全与高亮")
    void testSuggestWithCompletionSuggester() throws Exception {
        SearchResponse<CourseIndexDoc> mockResponse = mock(SearchResponse.class);

        // 构造 Suggestion Mock
        Suggestion<CourseIndexDoc> suggestion = mock(Suggestion.class);
        when(suggestion.isCompletion()).thenReturn(true);

        CompletionSuggest<CourseIndexDoc> completionSuggest = mock(CompletionSuggest.class);
        CompletionSuggestOption<CourseIndexDoc> option = mock(CompletionSuggestOption.class);

        CourseIndexDoc doc = CourseIndexDoc.builder()
                .courseId("2091648316809035780")
                .category("后端开发")
                .build();

        when(option.text()).thenReturn("Spring Cloud 微服务实战");
        when(option.source()).thenReturn(doc);
        when(option.score()).thenReturn(10.0);

        when(completionSuggest.options()).thenReturn(List.of(option));
        when(suggestion.completion()).thenReturn(completionSuggest);

        when(mockResponse.suggest()).thenReturn(Map.of("course-suggest", List.of(suggestion)));

        when(elasticsearchClient.search(any(Function.class), eq(CourseIndexDoc.class)))
                .thenReturn(mockResponse);

        SuggestResponse response = suggestService.suggest("Spring", 8);

        assertThat(response).isNotNull();
        assertThat(response.getSuggestions()).hasSize(1);

        SuggestItem item = response.getSuggestions().get(0);
        assertThat(item.getText()).isEqualTo("Spring Cloud 微服务实战");
        assertThat(item.getHighlight()).isEqualTo("<em>Spring</em> Cloud 微服务实战");
        assertThat(item.getCategory()).isEqualTo("后端开发");
        assertThat(item.getType()).isEqualTo("COURSE");
        assertThat(item.getTargetId()).isEqualTo("2091648316809035780");
        assertThat(item.getScore()).isEqualTo(10.0f);
    }

    @Test
    @DisplayName("测试当输入为空或空白字符时直接返回空建议")
    void testSuggestWithBlankPrefix() {
        SuggestResponse response1 = suggestService.suggest("", 8);
        assertThat(response1.getSuggestions()).isEmpty();

        SuggestResponse response2 = suggestService.suggest("   ", 8);
        assertThat(response2.getSuggestions()).isEmpty();

        SuggestResponse response3 = suggestService.suggest(null, 8);
        assertThat(response3.getSuggestions()).isEmpty();

        verifyNoInteractions(elasticsearchClient);
    }

    @Test
    @DisplayName("测试当 Completion 无结果时 fallback 到 MatchPhrasePrefix 查询")
    void testSuggestFallbackToPhrasePrefix() throws Exception {
        SearchResponse<CourseIndexDoc> completionResponse = mock(SearchResponse.class);
        when(completionResponse.suggest()).thenReturn(Collections.emptyMap());

        SearchResponse<CourseIndexDoc> phraseResponse = mock(SearchResponse.class);
        HitsMetadata<CourseIndexDoc> hitsMetadata = mock(HitsMetadata.class);
        Hit<CourseIndexDoc> hit = mock(Hit.class);
        CourseIndexDoc doc = CourseIndexDoc.builder()
                .courseId("3001")
                .title("Docker 容器实战")
                .category("DevOps")
                .build();
        when(hit.source()).thenReturn(doc);
        when(hit.score()).thenReturn(2.5);
        when(hitsMetadata.hits()).thenReturn(List.of(hit));
        when(phraseResponse.hits()).thenReturn(hitsMetadata);

        when(elasticsearchClient.search(any(Function.class), eq(CourseIndexDoc.class)))
                .thenReturn(completionResponse)
                .thenReturn(phraseResponse);

        SuggestResponse response = suggestService.suggest("Docker", 5);

        assertThat(response).isNotNull();
        assertThat(response.getSuggestions()).hasSize(1);
        assertThat(response.getSuggestions().get(0).getText()).isEqualTo("Docker 容器实战");
        assertThat(response.getSuggestions().get(0).getHighlight()).isEqualTo("<em>Docker</em> 容器实战");
        assertThat(response.getSuggestions().get(0).getTargetId()).isEqualTo("3001");
    }

    @Test
    @DisplayName("测试当 Elasticsearch 抛出异常时安全兜底返回空响应")
    void testSuggestWhenElasticsearchFails() throws Exception {
        when(elasticsearchClient.search(any(Function.class), eq(CourseIndexDoc.class)))
                .thenThrow(new IOException("Timeout"));

        SuggestResponse response = suggestService.suggest("Java", 8);

        assertThat(response).isNotNull();
        assertThat(response.getSuggestions()).isEmpty();
    }
}
