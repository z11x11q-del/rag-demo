package com.example.ragdemo.llm;

import com.example.ragdemo.config.LlmProperties;
import com.example.ragdemo.exception.LlmException;
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
import java.util.Map;

/**
 * OpenAI 兼容接口 LLM 客户端 — 默认接入 LongCat，可通过配置切换至任意兼容提供商
 * <p>
 * 切换提供商只需修改 {@code rag.llm.api-base-url}、{@code rag.llm.api-key}、
 * {@code rag.llm.model-name} 三项配置，代码无需改动。
 * </p>
 * <p>
 * 当 {@code rag.llm.api-key} 有值时激活；否则由 {@link com.example.ragdemo.config.StubBeanConfig}
 * 提供占位实现。
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rag.llm", name = "api-key")
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final LlmProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleLlmClient(LlmProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // ==================== 公开接口 ====================

    @Override
    public String chat(String prompt) {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", prompt)
        );
        return callWithRetry(messages);
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
        );
        return callWithRetry(messages);
    }

    @Override
    public Iterable<String> chatStream(String prompt) {
        throw new UnsupportedOperationException("chatStream not implemented in V1, planned for V3");
    }

    @Override
    public String modelName() {
        return properties.getModelName();
    }

    // ==================== 内部实现 ====================

    /**
     * 带指数退避重试的调用入口
     */
    private String callWithRetry(List<Map<String, String>> messages) {
        int maxRetries = properties.getMaxRetries();
        long delay = properties.getRetryDelayMs();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return doCall(messages);
            } catch (LlmException e) {
                if (!isRetryable(e) || attempt == maxRetries) {
                    throw e;
                }
                log.warn("LLM 调用失败，第 {}/{} 次重试，等待 {}ms，原因: {}",
                        attempt + 1, maxRetries, delay, e.getMessage());
                sleep(delay);
                delay = (long) (delay * properties.getRetryBackoffMultiplier());
            }
        }
        throw new LlmException("LLM 调用失败，已达最大重试次数: " + maxRetries);
    }

    /**
     * 发送单次 HTTP 请求并解析响应
     */
    private String doCall(List<Map<String, String>> messages) {
        try {
            String requestBody = buildRequestBody(messages);
            HttpRequest request = buildHttpRequest(requestBody);
            log.debug("LLM 请求: model={}, messages={}", properties.getModelName(), messages.size());

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return handleResponse(response);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM HTTP 请求异常: " + e.getMessage(), e);
        }
    }

    /**
     * 构建 OpenAI 兼容请求 JSON 体
     */
    private String buildRequestBody(List<Map<String, String>> messages) {
        try {
            var root = objectMapper.createObjectNode();
            root.put("model", properties.getModelName());
            root.put("temperature", properties.getTemperature());
            root.put("max_tokens", properties.getMaxTokens());
            root.put("stream", false);

            var messagesArray = root.putArray("messages");
            for (Map<String, String> msg : messages) {
                var msgNode = messagesArray.addObject();
                msgNode.put("role", msg.get("role"));
                msgNode.put("content", msg.get("content"));
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new LlmException("构建 LLM 请求体失败", e);
        }
    }

    /**
     * 构建 HTTP 请求对象
     */
    private HttpRequest buildHttpRequest(String requestBody) {
        String url = properties.getApiBaseUrl() + "/chat/completions";
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
    }

    /**
     * 处理响应：校验状态码，解析 choices[0].message.content
     */
    private String handleResponse(HttpResponse<String> response) {
        int status = response.statusCode();
        String body = response.body();

        if (status == 401) {
            throw new LlmException("LLM API Key 无效 (HTTP 401)");
        }
        if (status == 404) {
            throw new LlmException("LLM 模型不存在 (HTTP 404): " + properties.getModelName());
        }
        if (status == 429 || status == 500 || status == 502 || status == 503) {
            throw new LlmException(String.format("LLM API 错误 (HTTP %d)，将触发重试", status));
        }
        if (status != 200) {
            throw new LlmException(String.format("LLM API 异常 (HTTP %d): %s", status, body));
        }

        return parseAnswer(body);
    }

    /**
     * 从 OpenAI 兼容响应中提取 choices[0].message.content
     */
    private String parseAnswer(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new LlmException("LLM 响应缺少 choices 字段，原始响应: " + body);
            }
            JsonNode firstChoice = choices.get(0);

            // 处理内容安全过滤
            String finishReason = firstChoice.path("finish_reason").asText("");
            if ("content_filter".equals(finishReason)) {
                log.warn("LLM 响应被内容安全过滤，返回友好提示");
                return "抱歉，该内容无法展示，请尝试换一种方式提问。";
            }

            String content = firstChoice.path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new LlmException("LLM 响应 content 为空，原始响应: " + body);
            }

            log.debug("LLM 响应: {} 字符", content.length());
            return content.strip();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("解析 LLM 响应失败: " + e.getMessage(), e);
        }
    }

    /**
     * 判断异常是否可重试（限流 / 服务端错误 / 网络超时）
     */
    private boolean isRetryable(LlmException e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        return msg.contains("HTTP 429") || msg.contains("HTTP 500")
                || msg.contains("HTTP 502") || msg.contains("HTTP 503")
                || e.getCause() instanceof java.net.http.HttpTimeoutException;
    }

    /**
     * 线程休眠（中断时恢复中断标志）
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM 重试等待被中断", e);
        }
    }
}
