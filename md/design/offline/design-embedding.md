# Embedding 模块详细设计文档

## 1. 模块定位

`IngestionPipeline.doEmbed()` 是离线索引流水线中**Chunk 切分之后、索引写入之前**的关键环节：上游接收已切分的 Chunk 列表，下游输出与 Chunk 一一对应的稠密向量列表，供 Dense 向量索引写入。

```
Chunker 输出                 Embedding                    Indexing 输入
List<Chunk>  ──→  doEmbed(task, chunks)  ──→  List<float[]>
  ├── chunkId                                    │
  ├── content                                    └── 与 chunks 一一对应的稠密向量
  ├── titlePath
  └── tokenCount
```

**公共参考文档**：
- 模型规格：[shared-embedding-model-spec.md](../shared-embedding-model-spec.md)
- 客户端接口：[shared-embedding-client.md](../shared-embedding-client.md)

---

## 2. 在流水线中的位置

```
PARSING → STRUCTURING → CHUNKING → [EMBEDDING] → INDEXING → DONE
                                        ↑
                                    当前模块
                                  进度: 50% → 65%
```

Embedding 阶段在整个流水线中占据 **50%~65%** 的进度区间，是计算耗时最大的阶段（受限于远程 API 调用延迟）。

---

## 3. 核心流程设计

### 3.1 doEmbed 流程

```
输入: IngestionTask task, List<Chunk> chunks
                    │
                    ▼
          更新任务阶段为 EMBEDDING，进度 50%
                    │
                    ▼
          提取 chunks 的 content 列表
          texts = chunks.stream().map(Chunk::getContent).toList()
                    │
                    ▼
          调用 EmbeddingClient.embedBatch(texts)
          ┌─────────────────────────────────────┐
          │  内部按 batchSize=10 分片            │
          │  每片调用一次远程 API               │
          │  合并结果，保持与输入的顺序一致      │
          │  （详见 shared-embedding-client.md） │
          └─────────────────────────────────────┘
                    │
                    ▼
          获取模型名称: embeddingClient.modelName()
          记录到每个 chunk: chunk.setEmbeddingModel(modelName)
                    │
                    ▼
          日志记录: vectors 数量 + 模型名称
                    │
                    ▼
          更新任务进度 65%
                    │
                    ▼
          返回 List<float[]> vectors
```

### 3.2 当前实现

```java
private List<float[]> doEmbed(IngestionTask task, List<Chunk> chunks) {
    updateStage(task, TaskStage.EMBEDDING, 50);
    List<String> texts = chunks.stream().map(Chunk::getContent).toList();
    List<float[]> vectors = embeddingClient.embedBatch(texts);
    String modelName = embeddingClient.modelName();
    chunks.forEach(c -> c.setEmbeddingModel(modelName));
    log.info("Task {}: EMBEDDING done, vectors={}, model={}",
            task.getTaskId(), vectors.size(), modelName);
    updateStage(task, TaskStage.EMBEDDING, 65);
    return vectors;
}
```

---

## 4. 数据流转

### 4.1 输入

| 数据 | 来源 | 说明 |
|-----|------|------|
| `task` | IngestionPipeline.run() | 当前索引任务，用于更新阶段和进度 |
| `chunks` | doChunk() 阶段输出 | 已切分的文本块列表，每个 chunk 包含 content、chunkId 等 |

### 4.2 输出

| 数据 | 去向 | 说明 |
|-----|------|------|
| `List<float[]>` | doIndex() → writeDenseIndex() | 稠密向量列表，与 chunks 一一对应 |
| `chunk.embeddingModel` | MetadataStore | 记录生成向量所用的模型名称，持久化到元数据库 |

### 4.3 关键约束

- **顺序一致性**：`vectors[i]` 必须对应 `chunks[i]`，即输入文本与输出向量严格按序对应
- **全量完成**：所有 chunk 必须成功生成向量，任一失败则整个 Embedding 阶段失败
- **模型一致性**：同一批次的所有 chunk 使用同一模型生成向量，通过 `embeddingModel` 字段记录

---

## 5. 性能分析

### 5.1 耗时估算

| 参数 | 值 | 说明 |
|-----|-----|------|
| 单次 API 延迟 | 200-500ms | text-embedding-v4 单次调用（≤10 条） |
| batch_size | 10 | 单次最大文本数（模型限制） |
| 100 个 chunk | 10 次 API 调用 | 总耗时 2-5s |
| 500 个 chunk | 50 次 API 调用 | 总耗时 10-25s |
| 1000 个 chunk | 100 次 API 调用 | 总耗时 20-50s |

> Embedding 是离线流水线中**耗时最长**的阶段，远超解析、结构化、切分等 CPU 密集型操作。

### 5.2 瓶颈分析

```
流水线各阶段耗时占比（典型 500 chunk 文档）:

PARSING       ██                          ~2%   (< 1s)
STRUCTURING   ██                          ~2%   (< 1s)
CHUNKING      ████                        ~5%   (1-2s)
EMBEDDING     ████████████████████████████ ~80%  (10-25s)
INDEXING       ███████                    ~11%  (2-5s)
```

### 5.3 优化方向

| 优化策略 | 效果 | 复杂度 | 阶段 |
|---------|------|-------|------|
| 顺序分片调用（当前） | 基准 | 低 | 一期 |
| 并发分片调用（多线程） | 3-5x 提速 | 中 | 二期 |
| 本地模型推理（ONNX） | 消除网络延迟 | 高 | 三期 |
| 缓存相同内容的向量 | 减少重复调用 | 低 | 二期 |

---

## 6. 异常处理

### 6.1 异常场景与处理

| 异常场景 | 表现 | 处理方式 |
|---------|------|---------|
| API 调用超时 | SocketTimeoutException | EmbeddingClient 内部重试（详见公共文档） |
| API 限流 | HTTP 429 | 指数退避重试 |
| API Key 无效 | HTTP 401 | 直接抛出，任务标记 FAILED |
| 输入文本超长 | HTTP 400 | 理论上不会发生（Chunker 已限制 maxChunkSize） |
| 重试耗尽 | EmbeddingException | 抛出异常，由 IngestionPipeline.handleFailure() 兜底 |

### 6.2 异常传播链

```
EmbeddingClient.embedBatch() 抛出异常
        │
        ▼
doEmbed() 不捕获，异常向上传播
        │
        ▼
IngestionPipeline.run() 的 catch 块捕获
        │
        ▼
handleFailure(task, e)
  ├── task.status = FAILED
  ├── task.errorMessage = e.getMessage()
  ├── task.stage 保持为 EMBEDDING（记录失败阶段）
  └── document.status = FAILED
```

---

## 7. 与上下游的协作

### 7.1 与 Chunker（上游）的协作

```
Chunker 的输出质量直接影响 Embedding 效果:
  - chunk 内容越语义聚焦 → 生成的向量语义越明确
  - titlePath 注入到 content 头部 → 向量包含章节上下文信息
  - tokenCount ≤ maxChunkSize(1024) → 确保不超过模型的 max_seq_length(8192)
```

### 7.2 与 Indexing（下游）的协作

```
Embedding 的输出需要适配索引层:
  - vectors 与 chunks 严格一一对应 → writeDenseIndex 按序写入
  - embeddingModel 记录到 chunk 元数据 → 支持模型升级时区分向量版本
  - 向量维度(dimension) 必须与 VectorStore 索引维度一致
```

### 7.3 与 RetrievalService（在线检索）的协作

```
离线 Embedding 与在线 Embedding 必须保持一致:
  - 使用相同的模型（modelName）和维度（dimension）
  - 离线传 text_type="document"，在线传 text_type="query"
  - 模型升级时需要重新索引所有文档（或维护多版本向量）
```

---

## 8. embeddingModel 字段的意义

在 `doEmbed` 中，通过 `chunk.setEmbeddingModel(modelName)` 将模型名称记录到每个 chunk：

**核心价值**：

| 场景 | 作用 |
|-----|------|
| 模型升级 | 区分新旧模型生成的向量，支持增量重索引 |
| 多模型共存 | 不同文档类型可能使用不同 Embedding 模型 |
| 问题排查 | 检索质量下降时，可追溯向量由哪个模型生成 |
| 在线检索 | 确保 query 和 document 使用相同模型编码 |

**Chunk 实体对应字段**：

```java
public class Chunk {
    // ... 其他字段
    private String embeddingModel;  // 生成向量所用的模型名称及版本
}
```

---

## 9. 配置化设计

Embedding 相关配置集中管理，支持热切换：

```yaml
rag:
  embedding:
    # 模型配置（详见 shared-embedding-model-spec.md）
    model-name: text-embedding-v4
    dimension: 1024
    api-base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    api-key: ${DASHSCOPE_API_KEY}

    # 批量调用配置（详见 shared-embedding-client.md）
    batch-size: 10
    timeout-seconds: 30
    max-retries: 3
    retry-delay-ms: 1000
    retry-backoff-multiplier: 2.0
```

---

## 10. 进度管理

doEmbed 在流水线中管理 **50%~65%** 的进度区间：

| 时间点 | 进度 | 动作 |
|-------|------|------|
| 进入 EMBEDDING 阶段 | 50% | `updateStage(task, TaskStage.EMBEDDING, 50)` |
| Embedding 全部完成 | 65% | `updateStage(task, TaskStage.EMBEDDING, 65)` |

> **TODO（二期优化）**：对于大文档（chunk 数 > 100），可在分片调用过程中细粒度更新进度，如每完成 10% 的分片更新一次进度值。

---

## 11. 实现路线

### 11.1 一期实现范围

| 能力 | 说明 |
|-----|------|
| DashScope API 调用 | 基于 text-embedding-v4，OpenAI 兼容接口 |
| 批量分片调用 | 按 batchSize=10 分片，顺序调用 |
| 重试机制 | 指数退避，最多 3 次 |
| 模型名称记录 | chunk.embeddingModel 记录模型信息 |
| 进度更新 | 50%→65% 两点更新 |

### 11.2 后续 TODO

| 事项 | 优先级 | 说明 |
|-----|-------|------|
| 并发分片调用 | P1 | 多线程并发调用 API，提升大文档处理速度 |
| 向量缓存 | P2 | 相同内容的 chunk 跳过重复 Embedding |
| text_type 区分 | P2 | 离线传 `"document"`，在线传 `"query"`，优化向量质量 |
| 细粒度进度更新 | P2 | 大文档在分片调用过程中实时更新进度 |
| dense&sparse 混合 | P3 | 利用 text-embedding-v4 的混合向量能力替代独立 BM25 |
| 本地模型推理 | P3 | 基于 ONNX Runtime 本地推理，消除网络依赖 |
| Token 用量统计 | P2 | 利用响应中的 `usage.total_tokens` 统计成本 |
