# LLM 生成模块详细设计（LLM Generation Module）

## 1. 模块职责

封装大语言模型的调用逻辑，接收组装好的 Prompt 并返回生成的回答文本。支持同步调用和流式输出，屏蔽不同 LLM 提供商的差异。

---

## 2. 接口定义

### 2.1 LlmClient 接口

```java
package com.example.ragdemo.llm;

public interface LlmClient {

    /**
     * 同步调用 LLM 生成回答
     *
     * @param prompt 完整 Prompt
     * @return LLM 生成的回答文本
     */
    String chat(String prompt);

    /**
     * 流式调用 LLM（SSE），返回流式迭代器
     * MVP 阶段可只实现同步版本，流式版本后续扩展
     *
     * @param prompt 完整 Prompt
     * @return 回答片段的迭代器
     */
    Iterable<String> chatStream(String prompt);

    /**
     * 返回当前使用的模型名称
     */
    String modelName();
}
```

### 2.2 输入/输出说明

| 方法 | 输入 | 输出 | 说明 |
|------|------|------|------|
| `chat` | 完整 Prompt 字符串 | 完整回答文本 | 同步阻塞，等待 LLM 生成完毕 |
| `chatStream` | 完整 Prompt 字符串 | 文本片段迭代器 | 流式返回，逐 token/chunk 输出 |
| `modelName` | 无 | 模型名称 | 用于日志和可观测性 |

---

## 3. 详细设计

### 3.1 LongCat 接入方案（MVP）

MVP 阶段首先接入 **LongCat** 作为第三方 LLM 提供商，其接口兼容 OpenAI 格式，接入成本低。

#### API 信息

| 项目 | 值 |
|------|----|
| API Base URL | `https://api.longcat.chat/openai/v1` |
| 认证方式 | Bearer Token（`Authorization: Bearer {api-key}`） |
| 接口路径 | `/chat/completions` |
| 协议兼容 | OpenAI Chat Completions API |

#### 请求示例

```bash
curl --location 'https://api.longcat.chat/openai/v1/chat/completions' \
--header 'Authorization: Bearer ak_xxx' \
--header 'Content-Type: application/json' \
--data '{
    "model": "LongCat-Flash-Lite",
    "messages": [
        {
            "role": "user",
            "content": "你好"
        }
    ],
    "max_tokens": 1000,
    "temperature": 1,
    "stream": false
}'
```

#### 请求格式

```json
POST https://api.longcat.chat/openai/v1/chat/completions

{
    "model": "LongCat-Flash-Lite",
    "messages": [
        {"role": "system", "content": "{systemPrompt}"},
        {"role": "user", "content": "{context + query}"}
    ],
    "temperature": 1,
    "max_tokens": 1000,
    "stream": false
}
```

#### 响应格式（OpenAI 兼容）

```json
{
    "choices": [
        {
            "message": {
                "role": "assistant",
                "content": "根据参考文档[1]，..."
            },
            "finish_reason": "stop"
        }
    ],
    "usage": {
        "prompt_tokens": 1200,
        "completion_tokens": 350,
        "total_tokens": 1550
    }
}
```

### 3.2 DashScope 接入方案（备选）

项目 Embedding 模块已接入阿里云 DashScope（`text-embedding-v4`），后续可切换到 DashScope LLM，保持技术栈一致。

#### API 兼容性

DashScope 提供 OpenAI 兼容接口（`/compatible-mode/v1/chat/completions`），与 LongCat 接口格式一致，仅需修改 `api-base-url`、`api-key` 和 `model-name` 即可切换。

### 3.3 模型选型

#### LongCat 模型（MVP 首选）

| 模型 | 特点 | 适用场景 |
|------|------|--------|
| LongCat-Flash-Lite | 轻量快速，适合快速原型验证 | **MVP 推荐** |

#### DashScope 模型（备选）

| 模型 | 上下文窗口 | 特点 | 适用场景 |
|------|-----------|------|--------|
| qwen-plus | 128K | 性价比高，中文效果好 | 通用问答 |
| qwen-turbo | 128K | 速度快，成本低 | 低延迟场景 |
| qwen-max | 128K | 最高质量 | 复杂推理 |
| qwen-long | 1M | 超长上下文 | 大文档场景 |

MVP 阶段使用 **LongCat-Flash-Lite** 快速跑通链路，后续可按需切换到 DashScope 模型。

> 由于所有提供商均兼容 OpenAI 接口格式，切换模型只需修改配置文件中的 `api-base-url`、`api-key`、`model-name` 三个参数，无需改动代码。

### 3.4 实现类设计

由于 LongCat 和 DashScope 均兼容 OpenAI 接口，实现类统一命名为 `OpenAiCompatibleLlmClient`，通过配置切换不同提供商：

```java
@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(name = "rag.llm.enabled", havingValue = "true")
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final LlmProperties properties;
    private final RestTemplate restTemplate;  // 或 WebClient

    @Override
    public String chat(String prompt) {
        // 1. 构造请求
        Map<String, Object> request = buildRequest(prompt, false);

        // 2. 调用 API（带重试）
        String response = callWithRetry(request);

        // 3. 解析响应
        return extractAnswer(response);
    }

    @Override
    public Iterable<String> chatStream(String prompt) {
        // V3 实现：使用 WebClient 或 SSE 客户端
        throw new UnsupportedOperationException(
            "Stream not implemented in MVP");
    }

    @Override
    public String modelName() {
        return properties.getModelName();
    }
}
```

### 3.5 重试策略

| 参数 | 默认值 | 说明 |
|------|-------|------|
| 最大重试次数 | 3 | 包含首次请求 |
| 重试间隔 | 1000ms | 首次重试等待时间 |
| 退避倍数 | 2.0 | 指数退避：1s → 2s → 4s |
| 可重试状态码 | 429, 500, 502, 503 | 限流和服务端错误 |

```java
private String callWithRetry(Map<String, Object> request) {
    int retries = 0;
    long delay = properties.getRetryDelayMs();

    while (retries < properties.getMaxRetries()) {
        try {
            return doCall(request);
        } catch (LlmRetryableException e) {
            retries++;
            if (retries >= properties.getMaxRetries()) throw e;
            log.warn("LLM 调用失败，第 {} 次重试，等待 {}ms",
                     retries, delay);
            Thread.sleep(delay);
            delay *= properties.getRetryBackoffMultiplier();
        }
    }
    throw new LlmException("LLM 调用失败，已达最大重试次数");
}
```

### 3.6 流式输出设计（V3）

流式输出使用 Server-Sent Events (SSE) 协议：

```
请求: POST /chat/completions  { "stream": true, ... }

响应（SSE）:
data: {"choices":[{"delta":{"content":"根据"}}]}
data: {"choices":[{"delta":{"content":"参考文档"}}]}
data: {"choices":[{"delta":{"content":"[1]"}}]}
...
data: [DONE]
```

Controller 层对应改造：

```java
@GetMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> queryStream(@RequestBody RagQueryRequest request) {
    // V3: 流式输出
}
```

---

## 4. MVP 实现方案

### 4.1 核心实现

MVP 阶段仅实现 `chat()` 同步调用：

- 使用 `RestTemplate` 发送 HTTP 请求
- 接入 LongCat OpenAI 兼容接口
- 模型：`LongCat-Flash-Lite`
- `chatStream()` 抛出 `UnsupportedOperationException`

### 4.2 HTTP 客户端

使用 Spring 的 `RestTemplate`（同步），V3 流式阶段切换为 `WebClient`（异步）。

### 4.3 Prompt 格式化

LongCat / DashScope 兼容接口均支持 `messages` 数组。MVP 阶段将整个 Prompt 作为单条 user message：

```java
private Map<String, Object> buildRequest(String prompt, boolean stream) {
    return Map.of(
        "model", properties.getModelName(),
        "messages", List.of(
            Map.of("role", "user", "content", prompt)
        ),
        "temperature", properties.getTemperature(),
        "max_tokens", properties.getMaxTokens(),
        "stream", stream
    );
}
```

---

## 5. 配置项

```yaml
rag:
  llm:
    enabled: false                                          # 是否启用 LLM（MVP 开发阶段可关闭）
    model-name: LongCat-Flash-Lite                          # 模型名称（MVP: LongCat-Flash-Lite）
    api-base-url: https://api.longcat.chat/openai/v1        # API 地址（MVP: LongCat）
    api-key: ${LLM_API_KEY:}                                # API Key
    temperature: 1                                          # 生成温度
    max-tokens: 1000                                        # 最大生成 token 数
    timeout-seconds: 60                                     # 单次请求超时
    max-retries: 3                                          # 最大重试次数
    retry-delay-ms: 1000                                    # 重试初始间隔
    retry-backoff-multiplier: 2.0                           # 退避倍数
```

切换到 DashScope 时只需修改三项：

```yaml
    model-name: qwen-plus
    api-base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    api-key: ${DASHSCOPE_API_KEY:}
```

---

## 6. 异常处理

| 异常场景 | HTTP 状态 | 处理策略 |
|---------|----------|---------|
| API Key 无效 | 401 | 抛出 `LlmException`，不重试 |
| 限流 | 429 | 重试（退避等待） |
| 模型不存在 | 404 | 抛出 `LlmException`，不重试 |
| 服务端错误 | 500/502/503 | 重试 |
| 请求超时 | - | 重试，最终超时后抛出异常 |
| 响应格式异常 | 200 | 抛出 `LlmException`，记录原始响应体 |
| 内容安全过滤 | 200（finish_reason=content_filter） | 返回友好提示文本 |

### 熔断策略（V2）

当连续失败达到阈值时触发熔断，快速失败：

| 参数 | 默认值 | 说明 |
|------|-------|------|
| 熔断阈值 | 连续 5 次失败 | 触发熔断 |
| 熔断时长 | 30s | 熔断期间直接返回 fallback |
| fallback 行为 | 返回"服务暂时不可用，请稍后重试" | 友好提示 |

---

## 7. 演进规划

| 阶段 | 能力 | 说明 |
|------|------|------|
| MVP | 同步调用 LongCat-Flash-Lite | RestTemplate + 重试，OpenAI 兼容接口 |
| V1 | 多模型支持 | 配置化切换 qwen-plus / qwen-turbo / qwen-max |
| V2 | 熔断 + Fallback | 连续失败后快速失败，切换备用模型 |
| V2+ | 模型路由 | 按 intent / 成本 / 负载自动选择模型 |
| V3 | 流式输出（SSE） | WebClient + SSE，逐 token 流式返回 |
| V3+ | 本地 LLM | Ollama + 本地模型，私有化部署 |
