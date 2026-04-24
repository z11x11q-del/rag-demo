# 重排模块详细设计（Rerank Module）

## 1. 模块职责

对融合后的候选文档集进行精排，提升 TopN 结果的相关性。位于召回-融合之后、上下文构造之前，是提升检索精度的关键环节。

---

## 2. 接口定义

### 2.1 Reranker 接口

```java
package com.example.ragdemo.retrieval;

public interface Reranker {

    /**
     * 对候选结果进行重排序
     *
     * @param query      用户查询（改写后）
     * @param candidates 召回候选列表（融合后）
     * @param topN       保留 TopN 结果
     * @return 重排后的结果（按相关性降序，截取 TopN）
     */
    List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topN);
}
```

### 2.2 RerankerClient 接口

类比 `EmbeddingClient`，封装对外部 Reranker API 的 HTTP 调用，将网络层与业务层解耦：

```java
package com.example.ragdemo.retrieval;

public interface RerankerClient {

    /**
     * 调用外部 Reranker API，返回每个文档的相关性分数
     *
     * @param query     用户查询
     * @param documents 文档内容列表（顺序与 candidates 对应）
     * @return 相关性分数列表，顺序与 documents 一一对应（0~1，越大越相关）
     */
    List<Double> rerank(String query, List<String> documents);

    /** 模型名称 */
    String modelName();
}
```

**输入/输出说明**

| 方向 | 类型 | 说明 |
|------|------|------|
| 输入 query | `String` | 用户查询（改写后），与每个文档计算相关性 |
| 输入 candidates | `List<RetrievalResult>` | 融合后候选集，通常 topK × 通道数量级 |
| 输入 topN | `int` | 保留的最终结果数量 |
| 输出 | `List<RetrievalResult>` | 重排后按相关性降序排列的 topN 条，score 已更新为重排分数 |

### 2.3 组件依赖关系

```
Reranker (接口)
    ├── TruncationReranker          ← MVP，无外部依赖
    └── CrossEncoderReranker        ← V1，依赖 RerankerClient
            └── RerankerClient (接口)
                    └── DashScopeRerankerClient  ← V1 实现，调用 DashScope API
```

---

## 3. 详细设计

### 3.1 重排策略对比

| 策略 | 原理 | 精度 | 延迟 | 适用阶段 |
|------|------|------|------|---------|
| 截断（Truncation） | 按融合分数直接截取 TopN，无模型调用 | 低 | < 1ms | MVP |
| Cross-Encoder | query + doc 送入 Transformer，输出相关性分数（0~1） | 高 | 50~200ms | V1 |
| 粗排 + 精排 | 轻量模型粗筛（100→30），再 Cross-Encoder 精排（30→TopN） | 中高 | 30~100ms | V2 |

### 3.2 DashScopeRerankerClient 实现设计

#### API 规格

DashScope Reranker 通过 OpenAI 兼容模式提供，端点为：

```
POST {api-base-url}/rerank
```

**请求体（Cohere 兼容格式）**

```json
{
  "model": "gte-rerank",
  "query": "什么是向量数据库？",
  "documents": [
    "向量数据库是一种...",
    "关系型数据库是...",
    "Milvus 是一种..."
  ],
  "top_n": null,
  "return_documents": false
}
```

**响应体**

```json
{
  "id": "rerank-xxxx",
  "results": [
    { "index": 2, "relevance_score": 0.9431 },
    { "index": 0, "relevance_score": 0.8812 },
    { "index": 1, "relevance_score": 0.1203 }
  ],
  "meta": { "tokens": { "input_tokens": 256, "output_tokens": 0 } }
}
```

- `results[i].index`：对应原始 `documents` 列表中的位置
- `results[i].relevance_score`：相关性分数（0~1）
- `RerankerClient.rerank()` 返回时**按原始 index 重新排列**，确保顺序与入参 `documents` 一一对应

#### 类声明骨架

```java
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rag.rerank", name = "api-key")
public class DashScopeRerankerClient implements RerankerClient {

    private final RerankProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DashScopeRerankerClient(RerankProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<Double> rerank(String query, List<String> documents) {
        return callApiWithRetry(query, documents);
    }

    @Override
    public String modelName() { return properties.getModelName(); }
}
```

#### 核心方法分解

| 方法 | 职责 |
|------|------|
| `callApiWithRetry(query, docs)` | 带指数退避重试的入口，逻辑与 `DashScopeEmbeddingClient` 一致 |
| `callApi(query, docs)` | 单次 HTTP 调用：build request → send → handle response |
| `buildRequestBody(query, docs)` | 构造请求 JSON，`return_documents=false` 节省带宽 |
| `buildHttpRequest(body)` | 组装 HTTP 请求，URL = `{api-base-url}/rerank` |
| `handleResponse(response)` | 非 200 则抛异常，200 则调用 `parseScores()` |
| `parseScores(body, docCount)` | 解析 `results[].{index, relevance_score}`，按 index 还原为与入参等长的分数列表 |
| `isRetryable(e)` | 可重试条件：HTTP 429 / 500 / 503 / 网络超时 |
| `computeRetryDelay(attempt)` | 指数退避：`retryDelayMs × backoffMultiplier^attempt` |

#### 分数还原逻辑

API 返回的 `results` 按分数降序排列（非原始顺序），需还原至与入参 `documents` 一一对应的顺序：

```java
private List<Double> parseScores(String responseBody, int docCount) {
    Double[] scores = new Double[docCount];
    Arrays.fill(scores, 0.0);  // 未返回的文档默认分数为 0.0

    JsonNode results = objectMapper.readTree(responseBody).get("results");
    for (JsonNode item : results) {
        int index = item.get("index").asInt();
        double score = item.get("relevance_score").asDouble();
        scores[index] = score;
    }
    return Arrays.asList(scores);
}
```

### 3.3 CrossEncoderReranker 实现设计

#### 类声明

```java
@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(name = "rag.rerank.enabled", havingValue = "true")
public class CrossEncoderReranker implements Reranker {

    private final RerankerClient rerankerClient;
    private final RerankProperties properties;
}
```

激活条件 `rag.rerank.enabled=true`，`rag.rerank.api-key` 非空时 `DashScopeRerankerClient` 自动注册为 `RerankerClient` Bean。

#### rerank() 完整流程

```java
@Override
public List<RetrievalResult> rerank(String query,
        List<RetrievalResult> candidates, int topN) {

    if (candidates.isEmpty()) {
        return Collections.emptyList();
    }

    // 1. 候选数上限截断，避免超出 API 最大限制
    List<RetrievalResult> truncated = candidates.size() > properties.getMaxCandidates()
            ? candidates.subList(0, properties.getMaxCandidates())
            : candidates;

    try {
        // 2. 提取文本列表
        List<String> documents = truncated.stream()
                .map(RetrievalResult::getContent)
                .toList();

        // 3. 调用 RerankerClient
        List<Double> scores = rerankerClient.rerank(query, documents);

        // 4. 按分数回填
        for (int i = 0; i < truncated.size(); i++) {
            truncated.get(i).setScore(scores.get(i));
        }

        // 5. 阈值过滤 + 排序 + 截取 TopN
        return applyThresholdAndLimit(truncated, topN);

    } catch (Exception e) {
        // 6. 降级：Cross-Encoder 失败则直接截断（不抛异常，保障主流程可用）
        log.warn("CrossEncoderReranker 调用失败，降级为截断模式: {}", e.getMessage());
        return truncated.stream().limit(topN).toList();
    }
}
```

#### applyThresholdAndLimit() — 阈值过滤

```java
private List<RetrievalResult> applyThresholdAndLimit(
        List<RetrievalResult> results, int topN) {

    List<RetrievalResult> sorted = results.stream()
            .sorted(Comparator.comparingDouble(RetrievalResult::getScore).reversed())
            .toList();

    List<RetrievalResult> filtered = sorted.stream()
            .filter(r -> r.getScore() >= properties.getScoreThreshold())
            .limit(topN)
            .toList();

    // 保底：若全部被阈值过滤，至少保留分数最高的 minResults 条
    if (filtered.isEmpty()) {
        return sorted.stream().limit(properties.getMinResults()).toList();
    }
    return filtered;
}
```

### 3.4 阈值过滤策略

| 参数 | 默认值 | 说明 |
|------|-------|------|
| `score-threshold` | 0.0（不过滤） | 重排分数低于此值的结果被丢弃 |
| `min-results` | 1 | 阈值过滤后至少保留的结果数（防止全部被过滤导致上下文为空） |

逻辑：先按阈值过滤 + 截取 TopN；若过滤后结果数为 0，则取分数最高的 `min-results` 条作为兜底。

---

## 4. MVP 实现方案

MVP 阶段使用 `TruncationReranker`，直接按融合分数截取 TopN，无任何外部调用：

```java
@Slf4j
@Component
public class TruncationReranker implements Reranker {

    @Override
    public List<RetrievalResult> rerank(String query,
            List<RetrievalResult> candidates, int topN) {
        log.debug("截断重排: candidates={}, topN={}", candidates.size(), topN);
        return candidates.stream().limit(topN).toList();
    }
}
```

`rag.rerank.enabled` 未设置或为 `false` 时，`CrossEncoderReranker` 不注册，`TruncationReranker` 作为唯一的 `Reranker` Bean 生效。

---

## 5. 配置项

新增 `RerankProperties` 类（`@ConfigurationProperties(prefix = "rag.rerank")`）：

```yaml
rag:
  rerank:
    enabled: false                          # false = TruncationReranker；true = CrossEncoderReranker
    model-name: gte-rerank                  # DashScope Reranker 模型名称
    api-base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    api-key: ${DASHSCOPE_API_KEY:}          # 非空时 DashScopeRerankerClient 自动激活
    score-threshold: 0.0                    # 重排分数最低阈值（0.0 = 不过滤）
    min-results: 1                          # 阈值过滤后至少保留的结果数
    timeout-seconds: 5                      # 单次 API 请求超时（秒）
    max-candidates: 100                     # 送入 Reranker 的最大候选数（超出则预先截断）
    max-retries: 3                          # 最大重试次数
    retry-delay-ms: 500                     # 重试初始等待（ms）
    retry-backoff-multiplier: 2.0           # 指数退避倍数
```

**激活条件**

| 配置 | 激活的 Bean |
|------|------------|
| `enabled=false`（默认） | `TruncationReranker` |
| `enabled=true` + `api-key` 为空 | `TruncationReranker`（`DashScopeRerankerClient` 不注册，`CrossEncoderReranker` 启动失败需报错） |
| `enabled=true` + `api-key` 非空 | `CrossEncoderReranker` + `DashScopeRerankerClient` |

---

## 6. 降级策略

### 6.1 CrossEncoderReranker 内部降级

`CrossEncoderReranker.rerank()` 捕获所有异常并内联降级为截断逻辑，**不向外抛异常**，保障在线链路主流程不中断：

```
CrossEncoderReranker.rerank()
    ├── 正常路径：RerankerClient 调用成功 → 阈值过滤 → TopN 截取
    └── 异常路径：catch(Exception) → warn 日志 → 直接 .stream().limit(topN)
```

### 6.2 降级矩阵

| 场景 | 行为 |
|------|----- |
| API 超时（`HttpTimeoutException`） | 降级截断 + warn 日志 |
| API 返回非 2xx | 降级截断 + warn 日志 |
| API Key 未配置但 enabled=true | 应用启动阶段报错（Bean 注入失败），不允许进入运行时 |
| candidates 为空 | 直接返回空列表，不调用 API |
| topN > candidates.size() | 返回全量 candidates（不补齐） |
| 分数全部低于阈值 | 保底返回最高分 `min-results` 条 |

---

## 7. 演进规划

| 阶段 | 能力 | 说明 |
|------|------|------|
| MVP | 截断重排（已实现） | `TruncationReranker`，按融合分数直接截取 TopN |
| V1 | Cross-Encoder 重排（已设计） | `CrossEncoderReranker` + `DashScopeRerankerClient`，接入 DashScope `gte-rerank` |
| V1+ | 阈值过滤 | `score-threshold` > 0，低相关性结果丢弃，减少噪声进入上下文 |
| V2 | 粗排 + 精排 | 先轻量模型粗筛（候选 100→30），再 Cross-Encoder 精排（30→TopN），降低延迟 |
| V3 | 本地 Reranker | 部署 bge-reranker-v2-m3 本地推理服务，消除外部 API 依赖，降低成本与延迟 |
