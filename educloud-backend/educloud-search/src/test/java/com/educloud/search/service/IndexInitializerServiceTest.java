package com.educloud.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsAliasRequest;
import co.elastic.clients.elasticsearch.indices.PutAliasRequest;
import co.elastic.clients.elasticsearch.indices.PutAliasResponse;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.util.ObjectBuilder;
import com.educloud.search.config.ElasticsearchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndexInitializerServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    private ElasticsearchProperties properties;
    private IndexInitializerService indexInitializerService;

    @BeforeEach
    void setUp() {
        properties = new ElasticsearchProperties();
        properties.setAliasName("educloud_course_search");
        properties.setIndexPrefix("educloud_course_v");
        indexInitializerService = new IndexInitializerService(elasticsearchClient, properties);
    }

    @Test
    @DisplayName("当别名已存在时，不重复创建索引")
    void testInitializeIndexWhenAliasExists() throws IOException {
        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        BooleanResponse trueResponse = new BooleanResponse(true);
        when(indicesClient.existsAlias(any(Function.class))).thenReturn(trueResponse);

        boolean initialized = indexInitializerService.initializeIndexIfNotExists();

        assertThat(initialized).isTrue();
        verify(indicesClient, never()).create(any(Function.class));
        verify(indicesClient, never()).putAlias(any(Function.class));
    }

    @Test
    @DisplayName("当别名不存在时，成功创建物理索引并绑定别名")
    void testInitializeIndexWhenAliasDoesNotExist() throws IOException {
        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        BooleanResponse falseResponse = new BooleanResponse(false);
        when(indicesClient.existsAlias(any(Function.class))).thenReturn(falseResponse);
        when(indicesClient.exists(any(Function.class))).thenReturn(new BooleanResponse(false));

        CreateIndexResponse createResponse = mock(CreateIndexResponse.class);
        when(indicesClient.create(any(Function.class))).thenReturn(createResponse);

        PutAliasResponse aliasResponse = mock(PutAliasResponse.class);
        when(indicesClient.putAlias(any(Function.class))).thenReturn(aliasResponse);

        boolean initialized = indexInitializerService.initializeIndexIfNotExists();

        assertThat(initialized).isTrue();
        verify(indicesClient, times(1)).create(any(Function.class));
        verify(indicesClient, times(1)).putAlias(any(Function.class));
    }

    @Test
    @DisplayName("当 Elasticsearch 离线或抛出异常时，安全捕获不阻断容器启动")
    void testInitializeIndexWhenElasticsearchFails() throws IOException {
        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        when(indicesClient.existsAlias(any(Function.class))).thenThrow(new RuntimeException("Connection refused"));

        assertThatCode(() -> {
            boolean initialized = indexInitializerService.initializeIndexIfNotExists();
            assertThat(initialized).isFalse();
        }).doesNotThrowAnyException();
    }
}
