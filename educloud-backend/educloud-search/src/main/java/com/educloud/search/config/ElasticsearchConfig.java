package com.educloud.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch 8.14.0 客户端配置类
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ElasticsearchProperties.class)
public class ElasticsearchConfig {

    private final ElasticsearchProperties properties;

    @Bean(destroyMethod = "close")
    public RestClient restClient() {
        HttpHost host = new HttpHost(properties.getHost(), properties.getPort(), properties.getScheme());
        RestClientBuilder builder = RestClient.builder(host);

        // 设置连接与读取超时
        builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                .setConnectTimeout(properties.getConnectTimeoutMs())
                .setSocketTimeout(properties.getSocketTimeoutMs())
        );

        // 凭证与 Headers 设置
        builder.setHttpClientConfigCallback(httpClientBuilder -> {
            if (StringUtils.hasText(properties.getUsername()) && StringUtils.hasText(properties.getPassword())) {
                CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(
                        AuthScope.ANY,
                        new UsernamePasswordCredentials(properties.getUsername(), properties.getPassword())
                );
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            }
            return httpClientBuilder;
        });

        List<Header> defaultHeaders = new ArrayList<>();
        if (StringUtils.hasText(properties.getApiKey())) {
            defaultHeaders.add(new BasicHeader("Authorization", "ApiKey " + properties.getApiKey()));
        }
        if (!defaultHeaders.isEmpty()) {
            builder.setDefaultHeaders(defaultHeaders.toArray(new Header[0]));
        }

        log.info("Initialized Elasticsearch RestClient -> {}://{}:{}", properties.getScheme(), properties.getHost(), properties.getPort());
        return builder.build();
    }

    @Bean
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient, ObjectMapper objectMapper) {
        JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper(objectMapper);
        return new RestClientTransport(restClient, jsonpMapper);
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
