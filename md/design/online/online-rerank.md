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
     * @param query      用户查询
     * @param candidates 召回候选列表（融合后）
     * @param topN       保留 TopN 结果
     * @return 重排后的结果（按相关性降序，截取 TopN）
     */
    List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topN);
}
```

### 2.2 输入/输出说明

| 方向 | 类型 | 说明 |
|------|------|------|
| 输入 query | `String` | 用户查询（改写后），用于与 candidate 计算相关性 |
| 输入 candidates | `List<RetrievalResult>` | 融合后的候选集，通常 topK * 通道数 量级 |
| 输入 topN | `int` | 保留的最终结果数量 |
| 输出 | `List<RetrievalResult>` | 重排后按相关性降序排列的 topN 条结果，score 被更新为重排分数 |

---

## 3. 详细设计

### 3.1 重排策略对比

| 策略 | 原理 | 精度 | 延迟 | 适用阶段 |
|------|------|------|------|---------|
| 截断（Truncation） | 按原始/融合分数直接截取 TopN | 低 | < 1ms | MVP |
| Cross-Encoder | query + doc 拼接，Transformer 输出相关性分数 | 高 | 50~200ms | V1 |
| 粗排+精排 | 先轻量模型粗筛，再 Cross-Encoder 精排 | 中高 | 30~100ms | V2 |

### 3.2 Cross-Encoder 重排（V1）

Cross-Encoder 将 query 和 document 拼接为一个序列输入 Transformer，直接输出相关性分数（0~1）。

```
输入: [CLS] query [SEP] document [SEP]
输出: relevance_score (float, 0~1)
```

#### 模型选型

| 模型 | 来源 | 特点 |
|------|------|------|
| bge-reranker-v2-m3 | BAAI（北京智源） | 多语言支持，中文效果好，开源 |
| Cohere Rerank | Cohere API | 云端 API，无需部署，按调用付费 |
| DashScope Reranker | 阿里云 | 与现有 DashScope 生态一致 |

推荐：优先选择 **DashScope Reranker**，与项目已有的 DashScope Embedding 保持一致的 API 风格和账号体系。

#### 调用流程

```java
public List<RetrievalResult> rerank(String query,
        List<RetrievalResult> candidates, int topN) {

    // 1. 构造 rerank 请求：(query, document) pairs
    List<String> documents = candidates.stream()
        .map(RetrievalResult::getContent)
        .toList();

    // 2. 调用 Reranker API
    List<Double> scores = rerankerApi.rerank(query, documents);

    // 3. 更新分数并排序
    for (int i = 0; i < candidates.size(); i++) {
        candidates.get(i).setScore(scores.get(i));
    }

    // 4. 按分数降序排列 + 阈值过滤 + 截取 TopN
    return candidates.stream()
        .filter(r -> r.getScore() >= scoreThreshold)
        .sorted(Comparator.comparingDouble(RetrievalResult::getScore).reversed())
        .limit(topN)
        .toList();
}
```

### 3.3 阈值过滤策略

重排后对分数低于阈值的结果进行过滤，避免低质量文档污染上下文：

| 参数 | 默认值 | 说明 |
|------|-------|------|
| `score-threshold` | 0.0（MVP 不过滤） | 重排分数低于此值的结果被丢弃 |
| `min-results` | 1 | 过滤后至少保留的结果数（防止全部被过滤） |

逻辑：先按阈值过滤，若过滤后结果数 < `min-results`，则取分数最高的 `min-results` 条。

---

## 4. MVP 实现方案

MVP 阶段使用 **截断策略**，直接按原始分数截取 TopN：

```java
@Slf4j
@RequiredArgsConstructor
@Component
public class TruncationReranker implements Reranker {

    @Override
    public List<RetrievalResult> rerank(String query,
            List<RetrievalResult> candidates, int topN) {
        log.debug("Rerank: 截断模式, candidates={}, topN={}",
                  candidates.size(), topN);
        return candidates.stream()
            .limit(topN)
            .toList();
    }
}
```

该实现与当前 `StubBeanConfig` 中的 stub 行为一致，但作为独立组件便于后续替换。

---

## 5. 配置项

```yaml
rag:
  rerank:
    enabled: false                   # 是否启用 Cross-Encoder 重排（MVP 关闭）
    model-name: gte-rerank           # 重排模型名称
    api-base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    api-key: ${DASHSCOPE_API_KEY:}
    score-threshold: 0.0             # 最低分数阈值
    min-results: 1                   # 过滤后最少保留条数
    timeout-seconds: 5               # 单次重排超时
    max-candidates: 100              # 送入重排的最大候选数
```

---

## 6. 异常处理

| 异常场景 | 处理策略 |
|---------|---------|
| Reranker API 超时 | 降级到截断模式，按融合分数直接截取 TopN |
| Reranker API 返回错误 | 降级到截断模式 + warn 日志 |
| candidates 为空 | 直接返回空列表 |
| topN > candidates.size() | 返回全部 candidates（不补齐） |

---

## 7. 演进规划

| 阶段 | 能力 | 说明 |
|------|------|------|
| MVP | 截断重排 | 按原始分数直接截取 TopN |
| V1 | Cross-Encoder 重排 | 接入 DashScope Reranker API |
| V1+ | 阈值过滤 | 分数低于阈值的结果丢弃 |
| V2 | 粗排+精排 | 先轻量模型粗筛（100→30），再 Cross-Encoder 精排（30→TopN） |
| V3 | 本地 Reranker | 部署 bge-reranker 本地模型，降低延迟和成本 |
