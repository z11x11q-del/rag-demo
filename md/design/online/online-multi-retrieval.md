# 多路召回模块详细设计（Multi-Channel Retrieval）

## 1. 模块职责

从多个索引通道并行召回候选文档，通过 RRF 融合策略合并结果，再经重排精筛后返回最终检索结果。位于在线流水线第二阶段，是检索质量的核心决定环节。

**MVP 阶段即启用双路召回（Dense + BM25），不做单路降级**。后续通过通道抽象平滑扩展 Web Search 等第三方数据源。

---

## 2. 核心抽象

### 2.1 RetrievalChannel 接口

召回通道统一抽象，每路数据源实现此接口：

```java
package com.example.ragdemo.retrieval;

public interface RetrievalChannel {

    /**
     * 通道名称，用于日志和配置标识
     */
    String channelName();

    /**
     * 执行召回，返回候选结果列表（按相关性降序）
     *
     * @param query 预处理后的查询文本
     * @param topK  本通道返回的最大候选数
     * @return 候选结果列表
     */
    List<RetrievalResult> retrieve(String query, int topK);
}
```

### 2.2 RetrievalService 接口

```java
package com.example.ragdemo.retrieval;

public interface RetrievalService {

    /**
     * 多路召回 + RRF 融合 + 重排
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

### 2.3 DefaultRetrievalService 实现

```java
@Slf4j
@RequiredArgsConstructor
@Service
public class DefaultRetrievalService implements RetrievalService {

    private final List<RetrievalChannel> channels;   // 所有启用的通道，Spring 自动注入
    private final Reranker reranker;
    private final ContextBuilder contextBuilder;
}
```

`channels` 由 Spring 的 `List<RetrievalChannel>` 自动注入所有实现 Bean，无需手动枚举通道类型，新增通道只需注册一个新的 `@Component`。

---

## 3. 召回通道实现

### 3.1 DenseRetrievalChannel（稠密向量召回）

```java
@Slf4j
@RequiredArgsConstructor
@Component
public class DenseRetrievalChannel implements RetrievalChannel {

    private final VectorStore vectorStore;
    private final EmbeddingClient embeddingClient;

    @Override
    public String channelName() { return "dense"; }

    @Override
    public List<RetrievalResult> retrieve(String query, int topK) {
        float[] queryVector = embeddingClient.embed(query);
        return vectorStore.search(queryVector, topK);
    }
}
```

- Embedding 模型：`text-embedding-v4`（1024 维）
- 距离度量：COSINE（Milvus 配置）
- `score`：余弦相似度（0~1，越大越相关）

### 3.2 SparseRetrievalChannel（BM25 关键词召回）

```java
@Slf4j
@RequiredArgsConstructor
@Component
public class SparseRetrievalChannel implements RetrievalChannel {

    private final BM25Store bm25Store;

    @Override
    public String channelName() { return "sparse"; }

    @Override
    public List<RetrievalResult> retrieve(String query, int topK) {
        return bm25Store.search(query, topK);
    }
}
```

- 底层实现：Elasticsearch BM25
- `score`：BM25 分数（非归一化，跨通道不可直接比较）
- 优势：精确关键词匹配，适合专有名词、代码关键字等场景

### 3.3 WebSearchRetrievalChannel（Web Search 通道，V2 引入）

> 本节为前瞻性设计，MVP/V1 不实现，Bean 不注册。

Web Search 通道的特殊性：
- 返回的是实时网页内容，不存储在本地向量库或 BM25 索引中
- 结果格式为 URL + 摘要（snippet），不同于本地 `Chunk`
- 需要将网页内容转换为 `RetrievalResult`，与本地召回结果同构后才能参与融合和重排

#### 数据源适配

```java
@Slf4j
@RequiredArgsConstructor
// @Component  // V2 阶段手动注册，避免 MVP/V1 引入依赖
public class WebSearchRetrievalChannel implements RetrievalChannel {

    private final WebSearchClient webSearchClient;   // 封装 Bing/Tavily/Google API

    @Override
    public String channelName() { return "web"; }

    @Override
    public List<RetrievalResult> retrieve(String query, int topK) {
        List<WebSearchResult> webResults = webSearchClient.search(query, topK);
        return webResults.stream()
            .map(this::toRetrievalResult)
            .toList();
    }

    private RetrievalResult toRetrievalResult(WebSearchResult r) {
        return RetrievalResult.builder()
            .chunkId("web:" + r.getUrl())           // chunkId 以 "web:" 前缀区分来源
            .content(r.getSnippet())                // 使用 snippet 参与融合和重排
            .score(r.getRankScore())                // 搜索引擎原始排名分（0~1 归一化）
            .metadata(Map.of(
                "source", "web",
                "url", r.getUrl(),
                "title", r.getTitle(),
                "fetchedAt", Instant.now().toString()
            ))
            .build();
    }
}
```

#### Web Search 客户端接口

```java
public interface WebSearchClient {

    /**
     * 执行 Web 搜索，返回原始结果
     *
     * @param query 查询文本
     * @param topK  返回结果数
     * @return 搜索结果列表（按搜索引擎排名）
     */
    List<WebSearchResult> search(String query, int topK);
}
```

候选实现：
| 实现 | API | 特点 |
|------|-----|------|
| `BingWebSearchClient` | Bing Search API v7 | 微软，结果质量稳定 |
| `TavilyWebSearchClient` | Tavily Search API | 专为 LLM/RAG 场景设计，返回 snippet 质量高 |
| `GoogleWebSearchClient` | Google Custom Search API | 结果最全，配额限制较严 |

推荐优先对接 **Tavily**，其 API 返回的 snippet 经过针对 RAG 场景的优化，内容密度高，适合直接送入重排。

---

## 4. RRF 融合算法

### 4.1 融合接口

```java
public interface FusionStrategy {

    /**
     * 将多个通道的召回结果融合为单一排序列表
     *
     * @param channelResults key=通道名, value=该通道的召回结果（已按相关性降序）
     * @return 融合后按融合分数降序排列的候选列表
     */
    List<RetrievalResult> fuse(Map<String, List<RetrievalResult>> channelResults);
}
```

### 4.2 RRF 实现

**Reciprocal Rank Fusion (RRF)** 仅依赖排名位置，不依赖原始分数，天然支持异构通道（余弦相似度 vs BM25 分数 vs 搜索引擎排名分数量纲不同）。

#### 公式

```
RRF_score(d) = Σ  1 / (k + rank_i(d))
               i∈channels
```

- `d`：文档
- `k`：平滑常数（默认 60）
- `rank_i(d)`：文档 d 在第 i 通道中的排名（从 1 开始；未出现则不计入）

#### 实现

```java
@Component
public class RRFFusionStrategy implements FusionStrategy {

    private static final int RRF_K = 60;

    @Override
    public List<RetrievalResult> fuse(Map<String, List<RetrievalResult>> channelResults) {
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, RetrievalResult> resultMap = new HashMap<>();

        for (List<RetrievalResult> results : channelResults.values()) {
            for (int rank = 0; rank < results.size(); rank++) {
                RetrievalResult r = results.get(rank);
                rrfScores.merge(r.getChunkId(),
                    1.0 / (RRF_K + rank + 1), Double::sum);
                resultMap.putIfAbsent(r.getChunkId(), r);
            }
        }

        return rrfScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .map(e -> {
                RetrievalResult r = resultMap.get(e.getKey());
                r.setScore(e.getValue());   // 用 RRF 分数覆盖原始分数
                return r;
            })
            .toList();
    }
}
```

#### 三路 RRF 示例（引入 Web Search 后）

```
Dense 通道：  d1(rank=1), d3(rank=2), d5(rank=3), ...
Sparse 通道： d2(rank=1), d1(rank=2), d4(rank=3), ...
Web 通道：    w1(rank=1), w2(rank=2), d1(rank=3), ...

d1 的 RRF 分数 = 1/(60+1) + 1/(60+2) + 1/(60+3) ≈ 0.0164 + 0.0160 + 0.0157 = 0.0481
w1 的 RRF 分数 = 1/(60+1) ≈ 0.0164（只出现在 Web 通道）
```

文档 d1 在三路均出现，RRF 分数累加，自然排到融合列表前列。

---

## 5. 召回流程

### 5.1 双路召回（MVP/V1）

```
query (String)
    │
    ├────────────────────────────┐
    │                            │
    ▼                            ▼
┌──────────────────┐   ┌──────────────────┐
│ DenseRetrieval   │   │ SparseRetrieval  │
│ Channel          │   │ Channel          │
│                  │   │                  │
│ embed(query)     │   │ bm25Store        │
│     ↓            │   │   .search(query, │
│ vectorStore      │   │           topK)  │
│   .search(vec,   │   │                  │
│          topK)   │   │                  │
└────────┬─────────┘   └────────┬─────────┘
         │                      │
         └──────────┬───────────┘
                    │
                    ▼
           ┌────────────────┐
           │  RRF 融合       │  合并去重 + 按融合分数排序
           └────────┬───────┘
                    │
                    ▼
           ┌────────────────┐
           │  Reranker       │  精排 → 截取 TopN
           └────────┬───────┘
                    │
                    ▼
           List<RetrievalResult>（topN 条）
```

### 5.2 三路召回（V2，引入 Web Search）

```
query (String)
    │
    ├─────────────────┬──────────────────┐
    │                 │                  │
    ▼                 ▼                  ▼
┌─────────┐     ┌─────────┐       ┌──────────┐
│ Dense   │     │ Sparse  │       │   Web    │
│ Channel │     │ Channel │       │  Channel │
└────┬────┘     └────┬────┘       └────┬─────┘
     │               │                 │
     └───────────┬───┘─────────────────┘
                 │
                 ▼
        ┌────────────────┐
        │  三路 RRF 融合  │
        └────────┬───────┘
                 │
                 ▼
        ┌────────────────┐
        │  Reranker       │  跨来源统一精排（本地 + Web）
        └────────┬───────┘
                 │
                 ▼
        List<RetrievalResult>（topN 条，含来源标记）
```

### 5.3 retrieve() 完整实现

```java
@Override
public List<RetrievalResult> retrieve(String query, int topK, int topN) {
    // 1. 并行执行所有启用通道的召回
    Map<String, List<RetrievalResult>> channelResults = channels.parallelStream()
        .collect(Collectors.toMap(
            RetrievalChannel::channelName,
            ch -> {
                try {
                    return ch.retrieve(query, topK);
                } catch (Exception e) {
                    log.warn("通道 [{}] 召回失败，跳过: {}", ch.channelName(), e.getMessage());
                    return Collections.emptyList();
                }
            }
        ));

    // 2. 过滤全空的通道（失败的通道不参与融合）
    channelResults.values().removeIf(List::isEmpty);

    if (channelResults.isEmpty()) {
        log.warn("所有召回通道均无结果，返回空列表");
        return Collections.emptyList();
    }

    // 3. RRF 融合
    List<RetrievalResult> fused = fusionStrategy.fuse(channelResults);

    // 4. 重排 + 截取 TopN
    return reranker.rerank(query, fused, topN);
}
```

---

## 6. Web Search 与本地结果的融合与重排

### 6.1 融合阶段（RRF）

RRF 的通道无关性使其天然适合跨异构数据源融合：

| 通道 | 分数类型 | RRF 处理方式 |
|------|---------|-------------|
| Dense | 余弦相似度（0~1） | 仅用于通道内排序，不直接参与融合计算 |
| Sparse | BM25 分数（无上界） | 仅用于通道内排序，不直接参与融合计算 |
| Web Search | 搜索引擎排名（0~1 或整数位次） | 仅用于通道内排序，不直接参与融合计算 |

三路 RRF 只看**每路通道内的排名位置**，不跨通道比较原始分数，因此异构数据源可无缝融合。

### 6.2 重排阶段（Cross-Encoder）

重排时，本地 Chunk 和 Web Search snippet 统一作为文本字符串送入 Cross-Encoder：

```java
// Reranker 视角：所有 candidate 均为 (query, content) 对，不区分来源
List<String> documents = candidates.stream()
    .map(RetrievalResult::getContent)  // 本地 chunk content 或 web snippet
    .toList();

List<Double> scores = rerankerApi.rerank(query, documents);
```

Cross-Encoder 直接在 query 和内容之间计算语义相关性，与内容来源无关，因此本地文档和网页片段可在同一排序空间竞争。

**注意事项**：
- Web Search snippet 通常较短（100~300 字），与本地 chunk 长度相近，重排效果较好
- 如果 Web Search 返回全文（非 snippet），需在 `WebSearchRetrievalChannel` 内先截断至 max_chunk_length（如 512 tokens）再参与融合
- 重排后的 `RetrievalResult.metadata` 保留 `source` 和 `url` 字段，供 ContextBuilder 在构建上下文时添加来源引用

### 6.3 上下文标记

ContextBuilder 在处理 Web Search 来源的结果时，添加来源引用：

```java
// 示例：上下文中为 Web 来源内容添加引用标注
if ("web".equals(result.getMetadata().get("source"))) {
    sb.append(String.format("[来源: %s]\n", result.getMetadata().get("url")));
}
sb.append(result.getContent()).append("\n\n");
```

---

## 7. 配置项

```yaml
rag:
  retrieval:
    dense-enabled: true              # 启用向量召回（MVP 开启）
    sparse-enabled: true             # 启用 BM25 召回（MVP 开启）
    web-enabled: false               # 启用 Web Search 召回（V2 开启）
    rrf-k: 60                        # RRF 平滑常数
    default-top-k: 10                # 每通道默认 topK
    score-threshold: 0.0             # 融合后最低分数阈值
    parallel-retrieval: true         # 并行执行多路召回

  web-search:
    provider: tavily                 # 使用的 Web Search 提供商
    api-key: ${WEB_SEARCH_API_KEY:}
    max-results: 5                   # Web Search 单次最大结果数
    snippet-max-tokens: 512          # Snippet 最大 token 数（超出截断）
    timeout-seconds: 3               # Web Search 超时（高于本地召回）
```

---

## 8. 降级矩阵

| Dense | Sparse | Web | 行为 |
|-------|--------|-----|------|
| OK | OK | 禁用/失败 | 双路 RRF 融合 |
| OK | 失败 | 禁用 | 仅向量召回，跳过融合，直接重排 |
| 失败 | OK | 禁用 | 仅 BM25 召回，跳过融合，直接重排 |
| OK | OK | OK | 三路 RRF 融合 |
| OK | OK | 失败 | 双路 RRF 融合，记录 Web 通道失败日志 |
| 失败 | 失败 | - | 返回空结果 + error 日志 |

通道失败不影响其他通道：异常被捕获后该通道结果记为空列表，从融合计算中排除。

---

## 9. 异常处理

| 异常场景 | 处理策略 |
|---------|---------|
| EmbeddingClient 编码失败 | Dense 通道失败，降级到其他通道；若无其他通道则抛出异常 |
| VectorStore 不可用 | Dense 通道返回空列表，其他通道继续 |
| BM25Store 不可用 | Sparse 通道返回空列表，其他通道继续 |
| Web Search API 超时 | Web 通道超时（`timeout-seconds`），超时后返回空列表，不阻塞流程 |
| Web Search API 限流/错误 | Web 通道返回空列表 + warn 日志 |
| 所有通道无结果 | 返回空列表，后续 ContextBuilder 处理空上下文场景 |

---

## 10. 演进规划

| 阶段 | 通道 | 融合 | 重排 | 说明 |
|------|------|------|------|------|
| MVP | Dense + Sparse（BM25） | 双路 RRF | 截断（TruncationReranker） | 两路并行召回，无模型重排 |
| V1 | Dense + Sparse | 双路 RRF | Cross-Encoder（DashScope） | 接入 DashScope Reranker |
| V1+ | Dense + Sparse | 双路 RRF + 并行优化 | Cross-Encoder | `CompletableFuture` 显式并行，超时控制 |
| V2 | Dense + Sparse + Web | 三路 RRF | Cross-Encoder（跨来源） | 接入 Web Search，上下文引入来源引用 |
| V3 | Dense + Sparse + Web | 三路 RRF | 粗排+精排 | 先轻量模型粗筛（topK*3→50），再精排（50→topN） |
| V4 | + 图谱召回 | 多路 RRF | 精排 | 新增知识图谱实体关系扩展通道 |
