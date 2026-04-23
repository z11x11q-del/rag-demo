# 多路召回模块详细设计（Multi-Channel Retrieval）

## 1. 模块职责

从多个索引通道并行召回候选文档，通过融合策略合并结果，再经重排精筛后返回最终检索结果。位于在线流水线第二阶段，是检索质量的核心决定环节。

---

## 2. 接口定义

### 2.1 RetrievalService 接口

```java
package com.example.ragdemo.retrieval;

public interface RetrievalService {

    /**
     * 多路召回 + 融合 + 重排
     *
     * @param query 预处理后的查询文本
     * @param topK  每条召回通道返回的候选数
     * @param topN  重排后保留的最终结果数
     * @return 最终检索结果列表（按相关性降序）
     */
    List<RetrievalResult> retrieve(String query, int topK, int topN);

    /**
     * 构建上下文（委托给 ContextBuilder）
     */
    String buildContext(List<RetrievalResult> results);
}
```

### 2.2 实现类：DefaultRetrievalService

```java
@Slf4j
@RequiredArgsConstructor
@Service
public class DefaultRetrievalService implements RetrievalService {

    private final VectorStore vectorStore;
    private final BM25Store bm25Store;
    private final EmbeddingClient embeddingClient;
    private final Reranker reranker;
    private final ContextBuilder contextBuilder;
}
```

### 2.3 依赖的外部接口

| 接口 | 方法 | 用途 |
|------|------|------|
| `EmbeddingClient` | `embed(String text)` | 将 query 编码为稠密向量 |
| `VectorStore` | `search(float[] queryVector, int topK)` | 向量相似度 TopK 召回 |
| `BM25Store` | `search(String query, int topK)` | BM25 关键词 TopK 召回 |
| `Reranker` | `rerank(String query, List<RetrievalResult>, int topN)` | 候选集精排 |

---

## 3. 详细设计

### 3.1 召回流程

```
query (String)
    │
    ├─────────────────────────────────┐
    │                                 │
    ▼                                 ▼
┌────────────────────┐    ┌────────────────────┐
│  Dense Retrieval   │    │  Sparse Retrieval  │
│                    │    │                    │
│  embed(query)      │    │  BM25Store         │
│      ↓             │    │    .search(query,  │
│  VectorStore       │    │            topK)   │
│    .search(vec,    │    │                    │
│           topK)    │    │                    │
└────────┬───────────┘    └────────┬───────────┘
         │                         │
         └────────┬────────────────┘
                  │
                  ▼
         ┌───────────────┐
         │  RRF 融合      │  合并去重 + 按融合分数排序
         └───────┬───────┘
                 │
                 ▼
         ┌───────────────┐
         │  Reranker      │  精排 → 截取 TopN
         └───────┬───────┘
                 │
                 ▼
         List<RetrievalResult>（topN 条）
```

### 3.2 召回通道详细说明

#### Dense Retrieval（稠密向量召回）

```java
// 1. 将 query 编码为向量
float[] queryVector = embeddingClient.embed(query);

// 2. 向量相似度搜索
List<RetrievalResult> denseResults = vectorStore.search(queryVector, topK);
```

- 使用的 Embedding 模型：`text-embedding-v4`（1024 维）
- 距离度量：COSINE（在 Milvus 中配置）
- 返回结果的 `score` 为余弦相似度（0~1，越大越相似）

#### Sparse Retrieval（稀疏关键词召回）

```java
// BM25 关键词检索
List<RetrievalResult> sparseResults = bm25Store.search(query, topK);
```

- 底层实现：Elasticsearch BM25
- 返回结果的 `score` 为 BM25 分数（非归一化，值域不固定）
- 优势：精确关键词匹配，适合专有名词、代码关键字等场景

### 3.3 RRF 融合算法

**Reciprocal Rank Fusion (RRF)** 是一种无需校准分数的融合方法，仅基于排名位置计算融合分数。

#### 公式

```
RRF_score(d) = Σ  1 / (k + rank_i(d))
               i∈channels
```

其中：
- `d` 为文档
- `k` 为平滑常数（默认 60）
- `rank_i(d)` 为文档 d 在第 i 条通道中的排名（从 1 开始，未出现则不计入）

#### 伪代码

```java
private static final int RRF_K = 60;

public List<RetrievalResult> fuse(
        List<RetrievalResult> denseResults,
        List<RetrievalResult> sparseResults) {

    Map<String, Double> rrfScores = new HashMap<>();
    Map<String, RetrievalResult> resultMap = new HashMap<>();

    // Dense 通道排名
    for (int rank = 0; rank < denseResults.size(); rank++) {
        RetrievalResult r = denseResults.get(rank);
        rrfScores.merge(r.getChunkId(),
            1.0 / (RRF_K + rank + 1), Double::sum);
        resultMap.putIfAbsent(r.getChunkId(), r);
    }

    // Sparse 通道排名
    for (int rank = 0; rank < sparseResults.size(); rank++) {
        RetrievalResult r = sparseResults.get(rank);
        rrfScores.merge(r.getChunkId(),
            1.0 / (RRF_K + rank + 1), Double::sum);
        resultMap.putIfAbsent(r.getChunkId(), r);
    }

    // 按 RRF 分数降序排列
    return rrfScores.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .map(e -> {
            RetrievalResult r = resultMap.get(e.getKey());
            r.setScore(e.getValue());  // 用 RRF 分数覆盖原始分数
            return r;
        })
        .toList();
}
```

#### RRF 特点

| 优点 | 说明 |
|------|------|
| 无需分数校准 | 不同通道的分数量纲不同（余弦 vs BM25），RRF 只看排名不看分数 |
| 实现简单 | 无需调参（k=60 是经验值） |
| 效果稳定 | 在 BEIR 等基准测试上表现优于简单加权融合 |

### 3.4 retrieve() 完整实现流程

```java
@Override
public List<RetrievalResult> retrieve(String query, int topK, int topN) {
    // 1. Dense Retrieval
    float[] queryVector = embeddingClient.embed(query);
    List<RetrievalResult> denseResults = vectorStore.search(queryVector, topK);

    // 2. Sparse Retrieval
    List<RetrievalResult> sparseResults = bm25Store.search(query, topK);

    // 3. RRF 融合
    List<RetrievalResult> fused = fuse(denseResults, sparseResults);

    // 4. Rerank TopN
    return reranker.rerank(query, fused, topN);
}
```

---

## 4. MVP 实现方案

MVP 阶段仅启用 **单路向量召回**，不做融合：

```java
@Override
public List<RetrievalResult> retrieve(String query, int topK, int topN) {
    // MVP: 仅 Dense Retrieval
    float[] queryVector = embeddingClient.embed(query);
    List<RetrievalResult> results = vectorStore.search(queryVector, topK);

    // MVP: 直接截取 TopN（Reranker stub 已实现此逻辑）
    return reranker.rerank(query, results, topN);
}
```

MVP 不启用 BM25 通道的原因：
- 减少外部依赖（不强制要求 Elasticsearch 启动）
- 先验证向量召回 + LLM 生成的端到端链路

---

## 5. 配置项

```yaml
rag:
  retrieval:
    dense-enabled: true              # 启用向量召回（默认开启）
    sparse-enabled: false            # 启用 BM25 召回（MVP 关闭，V1 开启）
    rrf-k: 60                        # RRF 平滑常数
    default-top-k: 10               # 默认 topK
    score-threshold: 0.0            # 最低分数阈值，低于此值的结果丢弃
    parallel-retrieval: true        # 是否并行执行多路召回
```

---

## 6. 异常处理

| 异常场景 | 处理策略 |
|---------|---------|
| EmbeddingClient 编码失败 | 抛出 `EmbeddingException`，外层捕获后返回 500 |
| VectorStore 不可用 | 若 BM25 启用则降级到纯稀疏召回；否则抛出异常 |
| BM25Store 不可用 | 若 Dense 启用则降级到纯向量召回；否则抛出异常 |
| 两条通道均无结果 | 返回空列表，后续 ContextBuilder 处理空上下文场景 |
| 召回超时 | 设置单通道超时（如 2s），超时后使用已返回的结果继续流程 |

### 降级矩阵

| Dense | Sparse | 行为 |
|-------|--------|------|
| OK | OK | RRF 融合 |
| OK | 失败/禁用 | 仅向量召回结果直接进入重排 |
| 失败/禁用 | OK | 仅 BM25 结果直接进入重排 |
| 失败 | 失败 | 返回空结果 + warn 日志 |

---

## 7. 演进规划

| 阶段 | 能力 | 说明 |
|------|------|------|
| MVP | 单路向量召回 | `vectorStore.search()` → `reranker.rerank()` |
| V1 | 混合召回 + RRF | 启用 BM25，两路并行召回 + RRF 融合 |
| V1+ | 并行召回优化 | 使用 `CompletableFuture` 并行执行两路召回 |
| V2 | 缓存召回 | 热点 query 结果缓存，跳过召回直接返回 |
| V2+ | 置信度过滤 | 单路分数过低的结果直接丢弃，不参与融合 |
| V4 | 图谱召回 | 新增知识图谱召回通道，实体关系扩展 |
