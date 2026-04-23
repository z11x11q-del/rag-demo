package com.example.ragdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Query 预处理配置参数 — 支持通过 application.yml 外部化配置
 */
@Configuration
@ConfigurationProperties(prefix = "rag.query")
public class QueryProperties {

    /** 是否启用 LLM 改写 */
    private boolean rewriteEnabled = true;
    /** 是否启用 LLM 意图识别 */
    private boolean intentRecognitionEnabled = true;
    /** rawQuery 最大长度，超出则截断 */
    private int maxQueryLength = 2000;
    /** 是否缓存预处理结果 */
    private boolean cacheEnabled = false;
    /** 缓存 TTL（分钟） */
    private int cacheTtlMinutes = 30;
    /** 缓存最大条数 */
    private int cacheMaxSize = 10000;

    public boolean isRewriteEnabled() { return rewriteEnabled; }
    public void setRewriteEnabled(boolean rewriteEnabled) { this.rewriteEnabled = rewriteEnabled; }

    public boolean isIntentRecognitionEnabled() { return intentRecognitionEnabled; }
    public void setIntentRecognitionEnabled(boolean intentRecognitionEnabled) { this.intentRecognitionEnabled = intentRecognitionEnabled; }

    public int getMaxQueryLength() { return maxQueryLength; }
    public void setMaxQueryLength(int maxQueryLength) { this.maxQueryLength = maxQueryLength; }

    public boolean isCacheEnabled() { return cacheEnabled; }
    public void setCacheEnabled(boolean cacheEnabled) { this.cacheEnabled = cacheEnabled; }

    public int getCacheTtlMinutes() { return cacheTtlMinutes; }
    public void setCacheTtlMinutes(int cacheTtlMinutes) { this.cacheTtlMinutes = cacheTtlMinutes; }

    public int getCacheMaxSize() { return cacheMaxSize; }
    public void setCacheMaxSize(int cacheMaxSize) { this.cacheMaxSize = cacheMaxSize; }
}
