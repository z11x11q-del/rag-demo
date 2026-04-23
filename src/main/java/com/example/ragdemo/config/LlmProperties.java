package com.example.ragdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 客户端配置参数 — 支持通过 application.yml 外部化配置
 * <p>
 * 所有兼容 OpenAI 接口的提供商（LongCat、DashScope 等）均通过修改
 * {@code api-base-url}、{@code api-key}、{@code model-name} 三项切换，无需改动代码。
 * </p>
 */
@Configuration
@ConfigurationProperties(prefix = "rag.llm")
public class LlmProperties {

    /** 模型名称 */
    private String modelName = "LongCat-Flash-Lite";
    /** API Base URL（OpenAI 兼容接口） */
    private String apiBaseUrl = "https://api.longcat.chat/openai/v1";
    /** API Key */
    private String apiKey = "";
    /** 生成温度 */
    private double temperature = 1.0;
    /** 最大生成 token 数 */
    private int maxTokens = 1000;
    /** 单次请求超时（秒） */
    private int timeoutSeconds = 60;
    /** 最大重试次数 */
    private int maxRetries = 3;
    /** 重试初始间隔（毫秒） */
    private long retryDelayMs = 1000;
    /** 退避倍数（指数退避） */
    private double retryBackoffMultiplier = 2.0;

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public long getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }

    public double getRetryBackoffMultiplier() { return retryBackoffMultiplier; }
    public void setRetryBackoffMultiplier(double retryBackoffMultiplier) { this.retryBackoffMultiplier = retryBackoffMultiplier; }
}
