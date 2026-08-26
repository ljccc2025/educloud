package com.educloud.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 客户端与索引配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "educloud.search.elasticsearch")
public class ElasticsearchProperties {

    /** ES 主机地址 */
    private String host = "127.0.0.1";

    /** ES HTTP 端口 */
    private int port = 9200;

    /** 协议 scheme: http / https */
    private String scheme = "http";

    /** 认证用户名（可选） */
    private String username;

    /** 认证密码（可选） */
    private String password;

    /** API Key（可选） */
    private String apiKey;

    /** 连接超时时间（毫秒） */
    private int connectTimeoutMs = 2000;

    /** Socket 超时时间（毫秒） */
    private int socketTimeoutMs = 5000;

    /** 搜索别名名称 */
    private String aliasName = "educloud_course_search";

    /** 物理索引前缀 */
    private String indexPrefix = "educloud_course_v";
}
