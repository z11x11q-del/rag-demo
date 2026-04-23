# Query 预处理模块详细设计（Query Preprocessing）

## 1. 模块职责

对用户原始查询进行清洗、改写和意图识别，提升后续召回阶段的效果。位于在线流水线第一阶段。

---

## 2. 接口定义

### 2.1 QueryPreprocessor 接口

```java
package com.example.ragdemo.retrieval;

public interface QueryPreprocessor {

    ProcessedQuery process(String rawQuery);

    record ProcessedQuery(
        String rewrittenQuery,  // 清洗/改写后的查询文本
        String intent           // 识别到的意图（qa / summary / comparison 等）
    ) {}
}
```

### 2.2 输入/输出说明

| 方向 | 类型 | 说明 |
|------|------|------|
| 输入 | `String rawQuery` | 用户原始查询字符串，可能包含错别字、无意义符号、口语化表达 |
| 输出 | `ProcessedQuery` | Java 16 record，不可变数据对象 |
| 输出.rewrittenQuery | `String` | 清洗/改写后的查询，作为后续召回的输入 |
| 输出.intent | `String` | 查询意图标签，影响 Prompt 模板选择 |

### 2.3 意图枚举

| intent 值 | 含义 | 示例 |
|-----------|------|------|
| `qa` | 事实性问答（默认） | "Milvus 支持哪些索引类型？" |
| `summary` | 总结类 | "帮我总结一下这份文档的要点" |
| `comparison` | 对比类 | "HNSW 和 IVF_FLAT 有什么区别？" |
| `how_to` | 操作指南 | "如何配置 Elasticsearch 连接？" |

---

## 3. 详细设计

### 3.1 处理流水线

```
rawQuery
    │
    ▼
┌──────────────┐
│  Step 1: 清洗 │  去除首尾空白、连续空格归一、移除控制字符
└──────┬───────┘
       │
       ▼
┌──────────────────┐
│  Step 2: 规范化   │  全角转半角、统一标点符号
└──────┬───────────┘
       │
       ▼
┌──────────────────────┐
│  Step 3: Query 改写   │  LLM 改写，可配置降级
└──────┬───────────────┘
       │
       ▼
┌──────────────────────┐
│  Step 4: 意图识别     │  LLM 意图分类，可配置降级
└──────┬───────────────┘
       │
       ▼
ProcessedQuery(rewrittenQuery, intent)
```

### 3.2 Step 1 — 文本清洗（MVP 实现）

清洗规则按顺序执行：

```java
public String clean(String raw) {
    String result = raw.strip();                         // 去除首尾空白
    result = result.replaceAll("[\\p{Cntrl}]", "");      // 移除控制字符
    result = result.replaceAll("\\s+", " ");             // 连续空白归一
    return result;
}
```

### 3.3 Step 2 — 文本规范化（MVP 实现）

- 全角字母/数字转半角（中文输入法误输入场景）
- 统一中英文标点：中文问号 `？` → `?`

### 3.4 Step 3 — Query 改写

利用 LLM 将用户口语化查询改写为更精准的检索查询：

```
System: 你是一个查询改写助手。请将用户的口语化问题改写为更适合文档检索的查询。
        只输出改写后的查询，不要解释。
User: {rawQuery}
```

设计要点：
- 改写后保留原始 query 作为 fallback，若 LLM 改写超时/失败则使用原始 query
- 可配置是否启用改写（`rag.query.rewrite-enabled`）
- 改写结果可记录到日志，便于调试和评估改写效果

### 3.5 Step 4 — 意图识别

通过 LLM 判断查询意图，失败时降级为默认意图 `qa`：

```
System: 请判断以下查询的意图类型，只返回意图标签。
        可选标签：qa, summary, comparison, how_to
User: {query}
```

设计要点：
- LLM 返回结果需校验是否属于合法意图标签，非法值降级为 `qa`
- 可配置是否启用意图识别（`rag.query.intent-recognition-enabled`）
- 若 LLM 调用超时/失败，降级使用默认意图 `qa`，记录 warn 日志

---

## 4. 实现方案

### 4.1 实现类：`DefaultQueryPreprocessor`

```java
@Slf4j
@RequiredArgsConstructor
@Component
public class DefaultQueryPreprocessor implements QueryPreprocessor {

    private static final String DEFAULT_INTENT = "qa";
    private static final Set<String> VALID_INTENTS = Set.of("qa", "summary", "comparison", "how_to");

    private final LlmClient llmClient;
    private final QueryProperties queryProperties;

    @Override
    public ProcessedQuery process(String rawQuery) {
        // Step 1 + 2: 清洗 + 规范化
        String cleaned = clean(rawQuery);

        // Step 3: LLM 改写（失败时降级使用清洗后的 query）
        String rewritten = rewrite(cleaned);

        // Step 4: LLM 意图识别（失败时降级为默认意图 qa）
        String intent = recognizeIntent(rewritten);

        log.debug("Query预处理: raw='{}' → rewritten='{}', intent='{}'",
                  rawQuery, rewritten, intent);
        return new ProcessedQuery(rewritten, intent);
    }

    private String rewrite(String cleaned) {
        if (!queryProperties.isRewriteEnabled()) {
            return cleaned;
        }
        try {
            String prompt = "你是一个查询改写助手。请将用户的口语化问题改写为更适合文档检索的查询。"
                          + "只输出改写后的查询，不要解释。";
            String result = llmClient.chat(prompt, cleaned);
            return (result != null && !result.isBlank()) ? result.strip() : cleaned;
        } catch (Exception e) {
            log.warn("LLM 改写失败，降级使用原始 query: {}", e.getMessage());
            return cleaned;
        }
    }

    private String recognizeIntent(String query) {
        if (!queryProperties.isIntentRecognitionEnabled()) {
            return DEFAULT_INTENT;
        }
        try {
            String prompt = "请判断以下查询的意图类型，只返回意图标签。"
                          + "可选标签：qa, summary, comparison, how_to";
            String result = llmClient.chat(prompt, query);
            String intent = (result != null) ? result.strip().toLowerCase() : DEFAULT_INTENT;
            return VALID_INTENTS.contains(intent) ? intent : DEFAULT_INTENT;
        } catch (Exception e) {
            log.warn("LLM 意图识别失败，降级使用默认意图: {}", e.getMessage());
            return DEFAULT_INTENT;
        }
    }
}
```

### 4.2 替换 Stub

实现类注册为 Spring Bean 后，`StubBeanConfig` 中的 `@ConditionalOnMissingBean` 存根将自动退让。

---

## 5. 配置项

```yaml
rag:
  query:
    rewrite-enabled: true            # 是否启用 LLM 改写
    intent-recognition-enabled: true  # 是否启用 LLM 意图识别
    cache-enabled: false            # 是否缓存预处理结果
    cache-ttl-minutes: 30           # 缓存 TTL
    cache-max-size: 10000           # 缓存最大条数
```

---

## 6. 异常处理

| 异常场景 | 处理策略 |
|---------|---------|
| rawQuery 为空或纯空白 | 抛出 `IllegalArgumentException`，由全局异常处理器返回 400 |
| rawQuery 超长（> 2000 字符） | 截断至最大长度并记录 warn 日志 |
| LLM 改写超时/失败 | 降级使用清洗后的原始 query，记录 warn 日志 |
| 意图识别失败 | 降级使用默认意图 `qa` |

---

## 7. 演进规划

| 阶段 | 能力 | 说明 |
|------|------|------|
| V1 | 正则清洗 + 规范化 + LLM 改写 + LLM 意图识别 | 完整预处理能力，改写/意图均可配置降级 |
| V2 | 多查询生成 | 一题多解，生成多个子查询并行召回 |
| V3 | 高频 query 缓存 | 降低延迟，避免重复预处理 |
