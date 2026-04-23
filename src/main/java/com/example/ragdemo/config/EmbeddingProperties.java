package com.example.ragdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Embedding 配置参数 — 支持通过 application.yml 外部化配置
 */
@Configuration
@ConfigurationProperties(prefix = "rag.embedding")
public class EmbeddingProperties {

    /** 模型名称 */
    private String modelName = "text-embedding-v4";
    /** 向量维度 */
    private int dimension = 1024;
    /** API 地址 */
    private String apiBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    /** API Key */
    private String apiKey = "";

    /** 单次 API 最大文本数 */
    private int batchSize = 10;
    /** 单次调用超时（秒） */
    private int timeoutSeconds = 30;

    /** 最大重试次数 */
    private int maxRetries = 3;
    /** 初始重试延迟（毫秒） */
    private long retryDelayMs = 1000;
    /** 退避倍数 */
    private double retryBackoffMultiplier = 2.0;

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public int getDimension() { return dimension; }
    public void setDimension(int dimension) { this.dimension = dimension; }

    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public long getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }

    public double getRetryBackoffMultiplier() { return retryBackoffMultiplier; }
    public void setRetryBackoffMultiplier(double retryBackoffMultiplier) { this.retryBackoffMultiplier = retryBackoffMultiplier; }
}
