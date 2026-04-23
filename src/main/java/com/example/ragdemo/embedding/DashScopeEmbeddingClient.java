package com.example.ragdemo.embedding;

import com.example.ragdemo.config.EmbeddingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * DashScope Embedding 客户端 — 调用阿里云 DashScope text-embedding-v4 API
 * <p>
 * 基于 OpenAI 兼容接口，支持批量分片调用和指数退避重试。
 * 通过配置项 {@code rag.embedding.api-key} 激活，未配置时由 StubBeanConfig 提供占位实现。
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rag.embedding", name = "api-key")
public class DashScopeEmbeddingClient implements EmbeddingClient {

    private final EmbeddingProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DashScopeEmbeddingClient(EmbeddingProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public float[] embed(String text) {
        List<float[]> result = callApiWithRetry(List.of(text));
        return result.getFirst();
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        return doBatchEmbed(texts);
    }

    @Override
    public int dimension() {
        return properties.getDimension();
    }

    @Override
    public String modelName() {
        return properties.getModelName();
    }

    // ==================== 内部实现 ====================

    /**
     * 按 batchSize 分片，逐片调用 API，合并结果
     */
    private List<float[]> doBatchEmbed(List<String> texts) {
        int batchSize = properties.getBatchSize();
        List<float[]> allVectors = new ArrayList<>();

        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> batch = texts.subList(i, Math.min(i + batchSize, texts.size()));
            List<float[]> batchVectors = callApiWithRetry(batch);
            allVectors.addAll(batchVectors);
        }
        return allVectors;
    }

    /**
     * 带指数退避重试的 API 调用
     */
    private List<float[]> callApiWithRetry(List<String> batch) {
        int maxRetries = properties.getMaxRetries();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return callApi(batch);
            } catch (EmbeddingException e) {
                if (!isRetryable(e) || attempt == maxRetries) {
                    throw e;
                }
                long delay = computeRetryDelay(attempt);
                log.warn("Embedding API retry {}/{}, delay={}ms, error={}", attempt + 1, maxRetries, delay, e.getMessage());
                sleep(delay);
            }
        }
        throw new EmbeddingException("Exceeded max retries: " + maxRetries);
    }

    /**
     * 调用 DashScope Embedding API
     */
    private List<float[]> callApi(List<String> texts) {
        try {
            String requestBody = buildRequestBody(texts);
            HttpRequest request = buildHttpRequest(requestBody);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return handleResponse(response);
        } catch (EmbeddingException e) {
            throw e;
        } catch (Exception e) {
            throw new EmbeddingException("Embedding API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * 构建请求 JSON 体
     */
    private String buildRequestBody(List<String> texts) {
        try {
            var root = objectMapper.createObjectNode();
            root.put("model", properties.getModelName());
            root.put("dimension", properties.getDimension());

            if (texts.size() == 1) {
                root.put("input", texts.getFirst());
            } else {
                var inputArray = root.putArray("input");
                texts.forEach(inputArray::add);
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new EmbeddingException("Failed to build request body", e);
        }
    }

    /**
     * 构建 HTTP 请求
     */
    private HttpRequest buildHttpRequest(String requestBody) {
        String url = properties.getApiBaseUrl() + "/embeddings";
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
    }

    /**
     * 处理 HTTP 响应，解析向量列表
     */
    private List<float[]> handleResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        if (statusCode != 200) {
            throw buildApiException(statusCode, response.body());
        }
        return parseEmbeddings(response.body());
    }

    /**
     * 从响应 JSON 中解析 embedding 向量列表
     */
    private List<float[]> parseEmbeddings(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode dataArray = root.get("data");

            List<float[]> vectors = new ArrayList<>();
            for (JsonNode item : dataArray) {
                vectors.add(parseVector(item.get("embedding")));
            }
            return vectors;
        } catch (Exception e) {
            throw new EmbeddingException("Failed to parse embedding response", e);
        }
    }

    /**
     * 解析单个向量数组
     */
    private float[] parseVector(JsonNode embeddingNode) {
        float[] vector = new float[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) {
            vector[i] = embeddingNode.get(i).floatValue();
        }
        return vector;
    }

    /**
     * 根据 HTTP 状态码构建对应的异常
     */
    private EmbeddingException buildApiException(int statusCode, String body) {
        String message = String.format("Embedding API error: HTTP %d, body=%s", statusCode, body);
        return new EmbeddingException(message);
    }

    /**
     * 判断异常是否可重试（429/500/503/网络超时）
     */
    private boolean isRetryable(EmbeddingException e) {
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        return msg.contains("HTTP 429") || msg.contains("HTTP 500")
                || msg.contains("HTTP 503") || msg.contains("Timeout")
                || e.getCause() instanceof java.net.http.HttpTimeoutException;
    }

    /**
     * 计算指数退避延迟
     */
    private long computeRetryDelay(int attempt) {
        return (long) (properties.getRetryDelayMs() * Math.pow(properties.getRetryBackoffMultiplier(), attempt));
    }

    /**
     * 线程休眠（忽略中断异常）
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("Retry interrupted", ie);
        }
    }
}
