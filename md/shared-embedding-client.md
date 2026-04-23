# EmbeddingClient 接口设计（公共参考文档）

> 本文档为公共参考文档，定义 Embedding 客户端的统一接口规范，供离线索引流水线（Ingestion Pipeline）和在线检索流水线（Retrieval Pipeline）共同引用。

---

## 1. 接口定位

`EmbeddingClient` 是系统中所有 Embedding 调用的**统一抽象层**，屏蔽底层模型调用的本地/远程差异，为上层业务提供一致的文本向量化能力。

```
离线流水线                                          在线流水线
IngestionPipeline.doEmbed()                    RetrievalService.retrieve()
        │                                              │
        └──────────── EmbeddingClient ─────────────────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        DashScope API   本地推理    其他模型服务
        (text-embedding-v4)  (ONNX Runtime)  (OpenAI / BGE)
```

---

## 2. 核心接口定义

```java
public interface EmbeddingClient {

    /**
     * 单条文本 Embedding
     *
     * @param text 输入文本
     * @return 稠密向量
     */
    float[] embed(String text);

    /**
     * 批量文本 Embedding
     *
     * @param texts 文本列表
     * @return 向量列表（与输入一一对应）
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 返回向量维度
     */
    int dimension();

    /**
     * 返回当前使用的模型名称
     */
    String modelName();
}
```

### 2.1 方法职责

| 方法 | 调用场景 | 说明 |
|-----|---------|------|
| `embed(text)` | 在线检索（单条 query） | 低延迟，单次调用 |
| `embedBatch(texts)` | 离线索引（批量 chunk） | 高吞吐，内部分片调用 |
| `dimension()` | VectorStore 初始化 | 确保索引维度与模型一致 |
| `modelName()` | Chunk 元数据记录 | 记录生成向量所用的模型，支持模型升级追溯 |

---

## 3. 批量调用分片策略

text-embedding-v4 单次最多接受 **10 条文本**，`embedBatch` 实现需内部分片：

```
输入: List<String> texts (N 条)
                │
                ▼
        按 batchSize=10 分片
        ┌──────┬──────┬──────┐
        │ 0-9  │10-19 │20-N  │  ← 每片最多 10 条
        └──┬───┴──┬───┴──┬───┘
           │      │      │
           ▼      ▼      ▼
       API Call  API Call  API Call  ← 顺序调用（避免并发限流）
           │      │      │
           ▼      ▼      ▼
        合并结果，按原始顺序返回
```

**伪代码**：

```java
public List<float[]> embedBatch(List<String> texts) {
    List<float[]> allVectors = new ArrayList<>();
    for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
        List<String> batch = texts.subList(i, Math.min(i + BATCH_SIZE, texts.size()));
        List<float[]> batchVectors = callApi(batch);
        allVectors.addAll(batchVectors);
    }
    return allVectors;
}
```

---

## 4. 异常处理与重试策略

### 4.1 可重试异常

| 异常类型 | HTTP 状态码 | 重试策略 |
|---------|------------|---------|
| 限流 | 429 | 指数退避：1s → 2s → 4s，最多 3 次 |
| 服务不可用 | 503 | 指数退避：2s → 4s → 8s，最多 3 次 |
| 网络超时 | - | 固定间隔 2s，最多 3 次 |
| 服务端错误 | 500 | 指数退避：1s → 2s → 4s，最多 2 次 |

### 4.2 不可重试异常

| 异常类型 | HTTP 状态码 | 处理方式 |
|---------|------------|---------|
| 参数错误 | 400 | 直接抛出，上层处理 |
| 认证失败 | 401 | 直接抛出，需人工介入 |

### 4.3 重试实现

```java
/**
 * 带重试的 API 调用
 */
private List<float[]> callApiWithRetry(List<String> batch) {
    int maxRetries = 3;
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
        try {
            return callApi(batch);
        } catch (RateLimitException | ServiceUnavailableException e) {
            if (attempt == maxRetries) throw e;
            long delay = (long) Math.pow(2, attempt) * 1000;
            Thread.sleep(delay);
            log.warn("Embedding API retry {}/{}, delay={}ms", attempt + 1, maxRetries, delay);
        }
    }
    throw new EmbeddingException("Exceeded max retries");
}
```

---

## 5. 配置参数

```yaml
rag:
  embedding:
    # 模型配置
    model-name: text-embedding-v4          # 模型名称
    dimension: 1024                         # 向量维度
    api-base-url: https://dashscope.aliyuncs.com/compatible-mode/v1  # API 地址
    api-key: ${DASHSCOPE_API_KEY}          # API Key（环境变量注入）

    # 批量调用配置
    batch-size: 10                          # 单次 API 最大文本数
    timeout-seconds: 30                     # 单次调用超时（秒）

    # 重试配置
    max-retries: 3                          # 最大重试次数
    retry-delay-ms: 1000                    # 初始重试延迟（毫秒）
    retry-backoff-multiplier: 2.0           # 退避倍数
```

---

## 6. 离线 vs 在线调用差异

| 维度 | 离线索引（Ingestion） | 在线检索（Retrieval） |
|-----|---------------------|---------------------|
| 调用方法 | `embedBatch(texts)` | `embed(text)` |
| 输入规模 | 数十~数千条 chunk | 单条 query |
| text_type | `"document"` | `"query"` |
| 延迟要求 | 宽松（秒级可接受） | 严格（< 500ms） |
| 失败影响 | 任务标记 FAILED，可重试 | 直接返回错误，影响用户体验 |
| 超时配置 | 30s（批量调用） | 3-5s（单条调用） |
| 并发控制 | 顺序分片调用，避免限流 | 单次调用，无并发问题 |

---

## 7. 实现类扩展点

当前系统通过 Spring 依赖注入管理 `EmbeddingClient` 实现，支持按需切换：

```
EmbeddingClient (interface)
    │
    ├── DashScopeEmbeddingClient     ← 一期默认：调用阿里云 DashScope API
    │       └── 基于 text-embedding-v4，OpenAI 兼容接口
    │
    ├── OpenAiEmbeddingClient         ← 可选：调用 OpenAI Embedding API
    │       └── 基于 text-embedding-3-small/large
    │
    ├── LocalOnnxEmbeddingClient      ← 可选：本地 ONNX Runtime 推理
    │       └── 基于 BGE-M3 / bge-large-zh 本地模型
    │
    └── StubEmbeddingClient           ← 开发/测试：返回随机向量
            └── 用于单元测试和本地开发
```

**切换方式**：通过 Spring Profile 或配置项控制激活哪个实现类。

---

## 8. 监控指标

| 指标 | 说明 | 告警阈值 |
|-----|------|---------|
| `embedding.latency.p99` | 单次 API 调用 P99 延迟 | > 5s |
| `embedding.batch.latency.p99` | 批量调用总耗时 P99 | > 30s |
| `embedding.error.rate` | API 调用错误率 | > 5% |
| `embedding.retry.count` | 重试次数 | 持续 > 0 需关注 |
| `embedding.token.usage` | Token 消耗量 | 按日统计成本 |
