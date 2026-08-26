package com.educloud.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.educloud.search.config.ElasticsearchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * Elasticsearch 索引与别名自动初始化服务
 * 在应用启动时自动检查别名，若缺失则基于 course-v1.json 创建物理索引并绑定别名。
 * 具备异常安全保护，ES 离线时不阻断应用启动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexInitializerService implements ApplicationRunner {

    private static final String SCHEMA_PATH = "elasticsearch/course-v1.json";
    private static final String DEFAULT_INITIAL_INDEX = "educloud_course_v1";

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        initializeIndexIfNotExists();
    }

    /**
     * 检查别名是否存在，若不存在则创建物理索引并绑定别名
     *
     * @return 是否成功完成检查/初始化（ES 离线时返回 false，不抛出异常）
     */
    public boolean initializeIndexIfNotExists() {
        String aliasName = properties.getAliasName();
        String initialIndexName = properties.getIndexPrefix() != null ? properties.getIndexPrefix() + "1" : DEFAULT_INITIAL_INDEX;

        try {
            log.info("Checking Elasticsearch alias [{}]...", aliasName);
            BooleanResponse aliasExists = elasticsearchClient.indices().existsAlias(a -> a.name(aliasName));

            if (Boolean.TRUE.equals(aliasExists.value())) {
                log.info("Elasticsearch alias [{}] already exists. Skip initialization.", aliasName);
                return true;
            }

            log.info("Alias [{}] not found. Creating physical index [{}] from schema [{}]...", aliasName, initialIndexName, SCHEMA_PATH);
            ClassPathResource resource = new ClassPathResource(SCHEMA_PATH);
            if (!resource.exists()) {
                log.error("Elasticsearch schema resource [{}] not found in classpath!", SCHEMA_PATH);
                return false;
            }

            try (InputStream input = resource.getInputStream()) {
                elasticsearchClient.indices().create(c -> c.index(initialIndexName).withJson(input));
            }

            log.info("Physical index [{}] created successfully. Binding alias [{}]...", initialIndexName, aliasName);
            elasticsearchClient.indices().putAlias(a -> a.index(initialIndexName).name(aliasName));

            log.info("Successfully bound alias [{}] to index [{}].", aliasName, initialIndexName);
            return true;
        } catch (Exception e) {
            log.warn("Failed to initialize Elasticsearch index/alias [{} -> {}]: {}. Application will continue with database fallback mode.",
                    initialIndexName, aliasName, e.getMessage());
            return false;
        }
    }
}
