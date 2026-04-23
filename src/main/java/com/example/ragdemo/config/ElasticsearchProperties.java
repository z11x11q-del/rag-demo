package com.example.ragdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 连接与索引配置参数
 */
@Configuration
@ConfigurationProperties(prefix = "rag.elasticsearch")
public class ElasticsearchProperties {

    /** 是否启用 Elasticsearch BM25 存储（false 时回退到内存实现） */
    private boolean enabled = false;

    /** ES 服务地址，如 http://127.0.0.1:9200 */
    private String uri = "http://127.0.0.1:9200";

    /** 用户名（可选） */
    private String username = "";

    /** 密码（可选） */
    private String password = "";

    /** 索引名称 */
    private String indexName = "rag_bm25";

    /** 分片数 */
    private int numberOfShards = 1;

    /** 副本数 */
    private int numberOfReplicas = 0;

    /** 连接超时（毫秒） */
    private int connectTimeoutMs = 5000;

    /** 读取超时（毫秒） */
    private int socketTimeoutMs = 30000;

    // ---- getters & setters ----

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }

    public int getNumberOfShards() { return numberOfShards; }
    public void setNumberOfShards(int numberOfShards) { this.numberOfShards = numberOfShards; }

    public int getNumberOfReplicas() { return numberOfReplicas; }
    public void setNumberOfReplicas(int numberOfReplicas) { this.numberOfReplicas = numberOfReplicas; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getSocketTimeoutMs() { return socketTimeoutMs; }
    public void setSocketTimeoutMs(int socketTimeoutMs) { this.socketTimeoutMs = socketTimeoutMs; }
}
