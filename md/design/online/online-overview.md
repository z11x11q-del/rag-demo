# 在线检索流水线总览（Online Retrieval Pipeline Overview）

## 1. 模块职责

在线检索流水线是 RAG 系统的核心查询链路，负责接收用户查询、检索相关文档、生成回答并返回结构化结果。

---

## 2. 端到端数据流

```
用户输入 Query
    │
    ▼
┌─────────────────────────────────────────────┐
│  RagController  POST /api/rag/query         │
│  接收 RagQueryRequest { query, topK? }      │
└───────────────────┬─────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────┐
│  RagService.answer()  —  流水线编排          │
│                                             │
│  ① QueryPreprocessor.process(rawQuery)      │
│     → ProcessedQuery { rewrittenQuery,      │
│                        intent }             │
│                                             │
│  ② RetrievalService.retrieve(query,topK,N)  │
│     ├── EmbeddingClient.embed(query)        │
│     ├── VectorStore.search(vec, topK)       │
│     ├── BM25Store.search(query, topK)       │
│     ├── RRF 融合                            │
│     └── Reranker.rerank(query, cands, topN) │
│                                             │
│  ③ RetrievalService.buildContext(results)   │
│     → ContextBuilder.build(results)         │
│                                             │
│  ④ PromptBuilder.build(context, query,      │
│                         intent)             │
│                                             │
│  ⑤ LlmClient.chat(prompt)                  │
│                                             │
│  ⑥ PostProcessor.process(rawAnswer, results)│
│     → RagQueryResponse { answer, refs }     │
└───────────────────┬─────────────────────────┘
                    │
                    ▼
            返回 RagQueryResponse
```

---

## 3. 编排逻辑详细说明

编排中枢为 `RagService`（`com.example.ragdemo.service.RagService`），核心方法 `answer(RagQueryRequest)`。

### 3.1 TopK / TopN 策略

| 参数 | 含义 | 计算方式 |
|------|------|---------|
| `topK` | 多路召回阶段每条通道返回的候选数 | `request.topK`，默认 `10` |
| `topN` | 重排后保留的最终结果数 | `max(1, topK / 2)`，即 topK 的一半向下取整，最小为 1 |

设计意图：召回阶段宽泛覆盖（topK），重排阶段精准筛选（topN），保证召回率和精度的平衡。

### 3.2 六阶段流水线

| 阶段 | 组件 | 输入 | 输出 |
|------|------|------|------|
| 1. Query 预处理 | `QueryPreprocessor` | 原始 query 字符串 | `ProcessedQuery(rewrittenQuery, intent)` |
| 2. 多路召回+重排 | `RetrievalService` | rewrittenQuery, topK, topN | `List<RetrievalResult>` |
| 3. 上下文构造 | `ContextBuilder`（via RetrievalService） | `List<RetrievalResult>` | 格式化上下文字符串 |
| 4. Prompt 组装 | `PromptBuilder` | context, query, intent | 完整 Prompt 字符串 |
| 5. LLM 生成 | `LlmClient` | Prompt | LLM 原始回答 |
| 6. 后处理 | `PostProcessor` | rawAnswer, results | `RagQueryResponse` |

---

## 4. 接口总表

| 模块 | 接口 | 核心方法 | 职责 |
|------|------|---------|------|
| Query 预处理 | `QueryPreprocessor` | `process(String rawQuery)` | 清洗 / 改写 / 意图识别 |
| 向量检索 | `VectorStore` | `search(float[] queryVector, int topK)` | 稠密向量相似度检索 |
| 关键词检索 | `BM25Store` | `search(String query, int topK)` | BM25 稀疏检索 |
| 向量编码 | `EmbeddingClient` | `embed(String text)` | 将 query 编码为稠密向量 |
| 检索编排 | `RetrievalService` | `retrieve(query, topK, topN)` | 多路召回 + RRF 融合 + 重排 |
| 上下文构造 | `ContextBuilder` | `build(List<RetrievalResult>)` | 格式化检索结果为 LLM 上下文 |
| 重排 | `Reranker` | `rerank(query, candidates, topN)` | 精排候选文档 |
| Prompt 构建 | `PromptBuilder` | `build(context, query, intent)` | 组装系统指令+上下文+查询 |
| LLM 生成 | `LlmClient` | `chat(String prompt)` | 调用 LLM 生成回答 |
| 后处理 | `PostProcessor` | `process(rawAnswer, references)` | 引用补充 / 格式化 / 校验 |
| 问答门面 | `RagService` | `answer(RagQueryRequest)` | 对外统一问答入口 |

---

## 5. 核心数据结构

### RagQueryRequest（入参）

```java
public class RagQueryRequest {
    private String query;      // 用户查询
    private Integer topK;      // 可选，默认 10
}
```

### RagQueryResponse（出参）

```java
public class RagQueryResponse {
    private String answer;                      // LLM 生成的回答
    private List<ReferenceSource> references;   // 引用来源列表

    public static class ReferenceSource {
        private String chunkId;     // chunk 标识
        private String fileName;    // 来源文件名
        private String titlePath;   // 章节路径
        private String content;     // 片段内容
        private Double score;       // 相关性分数
    }
}
```

### RetrievalResult（内部流转）

```java
public class RetrievalResult {
    private String chunkId;
    private String content;
    private String fileName;
    private String titlePath;
    private double score;
}
```

---

## 6. 非功能性设计

### 6.1 性能指标

| 指标 | 目标值 | 说明 |
|------|-------|------|
| 端到端延迟（P99） | < 3s | 不含 LLM 首 token 时间 |
| 检索延迟（P99） | < 500ms | 召回 + 融合 + 重排 |
| 吞吐 | > 100 QPS | 在线查询并发 |

### 6.2 可靠性

| 场景 | 降级策略 |
|------|---------|
| 向量服务不可用 | 降级到纯 BM25 稀疏召回兜底 |
| BM25 服务不可用 | 降级到纯向量召回 |
| LLM 调用超时/报错 | 熔断后返回友好提示，或切换 fallback 模型 |
| Reranker 不可用 | 跳过重排，直接使用 RRF 融合分数截取 TopN |

### 6.3 可观测性

- **全链路追踪**：从 query 输入到 answer 输出，记录每阶段耗时（预处理耗时、召回耗时、重排耗时、LLM 耗时、后处理耗时）
- **中间结果日志**：记录 rewrittenQuery、intent、召回数量、重排后数量、Prompt token 数
- **效果评估**：定期抽样人工评估，计算回答准确率、召回率

---

## 7. 演进路线

| 阶段 | 目标 | 关键能力 | 对应模块变更 |
|------|------|---------|-------------|
| MVP | 单路向量召回 + 云端 LLM | 跑通核心链路 | `DefaultRetrievalService` 仅走向量通道；`LlmClient` 接入通义千问 |
| V1 | 混合召回 + 重排 | 提升召回质量 | 启用 BM25 通道 + RRF 融合；接入 Cross-Encoder 重排 |
| V2 | Query 改写 + 意图识别 + 缓存 | 降低延迟、提升体验 | `QueryPreprocessor` LLM 改写；高频 query 缓存 |
| V3 | 本地 LLM + 流式输出 | 私有化、降低成本 | `LlmClient.chatStream()` SSE 流式；本地模型部署 |
| V4 | 知识图谱 + Agent 能力 | 复杂推理、多跳问答 | 新增图谱召回通道；Agent 编排多步推理 |

---

## 8. 子模块详细设计文档索引

| 模块 | 文档 |
|------|------|
| Query 预处理 | [online-query-preprocessing.md](online-query-preprocessing.md) |
| 多路召回 | [online-multi-retrieval.md](online-multi-retrieval.md) |
| 重排 | [online-rerank.md](online-rerank.md) |
| 上下文构造 | [online-context-builder.md](online-context-builder.md) |
| Prompt 构建 | [online-prompt-builder.md](online-prompt-builder.md) |
| LLM 生成 | [online-llm.md](online-llm.md) |
| 后处理 | [online-post-processing.md](online-post-processing.md) |
