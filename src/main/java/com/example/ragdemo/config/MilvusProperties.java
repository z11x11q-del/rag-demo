package com.example.ragdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 连接与集合配置参数
 */
@Configuration
@ConfigurationProperties(prefix = "rag.milvus")
public class MilvusProperties {

    /** 是否启用 Milvus 向量存储（false 时回退到内存实现） */
    private boolean enabled = false;

    /** Milvus 服务地址，如 http://127.0.0.1:19530 */
    private String uri = "http://127.0.0.1:19530";

    /** 用户名（可选） */
    private String username = "";

    /** 密码（可选） */
    private String password = "";

    /** 数据库名称 */
    private String dbName = "default";

    /** 集合名称 */
    private String collectionName = "rag_vectors";

    /** chunk_id 主键最大长度 */
    private int maxIdLength = 256;

    /** metadata JSON 字段最大长度 */
    private int maxMetadataLength = 8192;

    /** 索引类型：HNSW / IVF_FLAT 等 */
    private String indexType = "HNSW";

    /** 度量类型：COSINE / L2 / IP */
    private String metricType = "COSINE";

    /** HNSW 参数 M */
    private int hnswM = 16;

    /** HNSW 参数 efConstruction */
    private int hnswEfConstruction = 256;

    /** 搜索时 HNSW ef 参数 */
    private int searchEf = 128;

    /** 一致性级别：STRONG / BOUNDED / EVENTUALLY */
    private String consistencyLevel = "EVENTUALLY";

    // ---- getters & setters ----

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDbName() { return dbName; }
    public void setDbName(String dbName) { this.dbName = dbName; }

    public String getCollectionName() { return collectionName; }
    public void setCollectionName(String collectionName) { this.collectionName = collectionName; }

    public int getMaxIdLength() { return maxIdLength; }
    public void setMaxIdLength(int maxIdLength) { this.maxIdLength = maxIdLength; }

    public int getMaxMetadataLength() { return maxMetadataLength; }
    public void setMaxMetadataLength(int maxMetadataLength) { this.maxMetadataLength = maxMetadataLength; }

    public String getIndexType() { return indexType; }
    public void setIndexType(String indexType) { this.indexType = indexType; }

    public String getMetricType() { return metricType; }
    public void setMetricType(String metricType) { this.metricType = metricType; }

    public int getHnswM() { return hnswM; }
    public void setHnswM(int hnswM) { this.hnswM = hnswM; }

    public int getHnswEfConstruction() { return hnswEfConstruction; }
    public void setHnswEfConstruction(int hnswEfConstruction) { this.hnswEfConstruction = hnswEfConstruction; }

    public int getSearchEf() { return searchEf; }
    public void setSearchEf(int searchEf) { this.searchEf = searchEf; }

    public String getConsistencyLevel() { return consistencyLevel; }
    public void setConsistencyLevel(String consistencyLevel) { this.consistencyLevel = consistencyLevel; }
}
