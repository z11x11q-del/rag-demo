# 后处理模块详细设计（Post-Processing Module）

## 1. 模块职责

对 LLM 生成的原始回答进行加工，包括引用补充、格式化、敏感校验和置信度标记，并组装最终的 `RagQueryResponse` 返回给用户。位于在线流水线最后一个阶段。

---

## 2. 接口定义

### 2.1 PostProcessor 接口

```java
package com.example.ragdemo.retrieval;

public interface PostProcessor {

    /**
     * 对 LLM 生成的原始回答进行后处理，组装完整响应
     *
     * @param rawAnswer  LLM 原始输出
     * @param references 检索引用来源（重排后的 TopN 结果）
     * @return 包含回答和引用来源的完整响应
     */
    RagQueryResponse process(String rawAnswer, List<RetrievalResult> references);
}
```

### 2.2 输入/输出说明

| 方向 | 类型 | 说明 |
|------|------|------|
| 输入 rawAnswer | `String` | LLM 原始输出文本，可能包含 `[1]`、`[2]` 等引用标记 |
| 输入 references | `List<RetrievalResult>` | 重排后的检索结果，编号与上下文中的 `[n]` 对应 |
| 输出 | `RagQueryResponse` | 包含处理后的 answer 和结构化的 references 列表 |

### 2.3 RagQueryResponse 结构

```java
public class RagQueryResponse {
    private String answer;                      // 处理后的回答文本
    private List<ReferenceSource> references;   // 引用来源列表

    public static class ReferenceSource {
        private String chunkId;     // chunk 标识
        private String fileName;    // 来源文件名
        private String titlePath;   // 章节路径
        private String content;     // 片段内容（可截取摘要）
        private Double score;       // 相关性分数
    }
}
```

---

## 3. 详细设计

### 3.1 处理流程

```
rawAnswer + List<RetrievalResult>
    │
    ▼
┌───────────────────────┐
│  Step 1: 引用标记提取  │  从 rawAnswer 中提取 [1]、[2] 等标记
└──────┬────────────────┘
       │
       ▼
┌───────────────────────┐
│  Step 2: 引用映射构建  │  将 [n] 映射到 references[n-1]
└──────┬────────────────┘
       │
       ▼
┌───────────────────────┐
│  Step 3: 格式化       │  Markdown 美化（可选）
└──────┬────────────────┘
       │
       ▼
┌───────────────────────┐
│  Step 4: 敏感校验     │  过滤敏感内容（V2）
└──────┬────────────────┘
       │
       ▼
┌───────────────────────┐
│  Step 5: 日志记录     │  记录全链路摘要
└──────┬────────────────┘
       │
       ▼
RagQueryResponse { answer, references }
```

### 3.2 Step 1 — 引用标记提取

从 LLM 回答中提取引用编号（`[1]`、`[2]` 等）：

```java
private Set<Integer> extractCitations(String answer) {
    Set<Integer> citations = new TreeSet<>();
    Matcher matcher = Pattern.compile("\\[(\\d+)]").matcher(answer);
    while (matcher.find()) {
        citations.add(Integer.parseInt(matcher.group(1)));
    }
    return citations;
}
```

### 3.3 Step 2 — 引用映射构建

将提取到的编号映射为 `ReferenceSource` 列表：

```java
private List<RagQueryResponse.ReferenceSource> buildReferences(
        Set<Integer> citations, List<RetrievalResult> results) {

    List<RagQueryResponse.ReferenceSource> refs = new ArrayList<>();
    for (int idx : citations) {
        if (idx < 1 || idx > results.size()) continue;  // 越界跳过

        RetrievalResult r = results.get(idx - 1);  // [1] 对应 index 0
        RagQueryResponse.ReferenceSource ref = new RagQueryResponse.ReferenceSource();
        ref.setChunkId(r.getChunkId());
        ref.setFileName(r.getFileName());
        ref.setTitlePath(r.getTitlePath());
        ref.setContent(truncateContent(r.getContent(), 200));  // 摘要截取
        ref.setScore(r.getScore());
        refs.add(ref);
    }
    return refs;
}
```

**摘要截取**：引用来源中的 content 字段截取前 200 字符作为摘要，避免响应体过大。

### 3.4 Step 3 — 格式化

对 LLM 输出做轻量格式化处理：

| 处理 | 说明 |
|------|------|
| 去除首尾空白 | `answer.strip()` |
| 去除多余空行 | 连续 3 个以上换行归一为 2 个 |
| 保留 Markdown | 不破坏 LLM 输出的 Markdown 格式 |

```java
private String formatAnswer(String rawAnswer) {
    String result = rawAnswer.strip();
    result = result.replaceAll("\n{3,}", "\n\n");
    return result;
}
```

### 3.5 Step 4 — 敏感校验（V2）

V2 阶段增加敏感内容过滤：

- 关键词黑名单匹配
- 正则模式匹配（如手机号、身份证号等 PII 信息）
- 可选：接入外部内容安全 API

### 3.6 Step 5 — 日志记录

记录全链路摘要信息，用于效果分析和问题排查：

```java
log.info("RAG 全链路完成: query='{}', citationCount={}, "
    + "referenceCount={}, answerLength={}",
    query, citations.size(), refs.size(), answer.length());
```

详细日志（debug 级别）：
- 原始 query
- 改写后 query
- 识别到的 intent
- 召回数量
- 重排后数量
- 引用编号列表
- LLM 模型名称
- 各阶段耗时

---

## 4. MVP 实现方案

### 4.1 实现类：DefaultPostProcessor

MVP 阶段实现引用补充 + 基础格式化：

```java
@Slf4j
@RequiredArgsConstructor
@Component
public class DefaultPostProcessor implements PostProcessor {

    private static final int CONTENT_SUMMARY_LENGTH = 200;
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)]");

    @Override
    public RagQueryResponse process(String rawAnswer,
            List<RetrievalResult> references) {

        // Step 1: 格式化回答
        String answer = formatAnswer(rawAnswer);

        // Step 2: 提取引用标记
        Set<Integer> citations = extractCitations(answer);

        // Step 3: 构建引用来源列表
        List<RagQueryResponse.ReferenceSource> refs;
        if (citations.isEmpty()) {
            // LLM 未标注引用，返回全部 reference 供前端展示
            refs = buildAllReferences(references);
        } else {
            // 仅返回被引用的 reference
            refs = buildReferences(citations, references);
        }

        // Step 4: 组装响应
        RagQueryResponse response = new RagQueryResponse();
        response.setAnswer(answer);
        response.setReferences(refs);

        log.debug("后处理完成: answerLength={}, citations={}, refs={}",
                  answer.length(), citations, refs.size());
        return response;
    }
}
```

### 4.2 无引用标记的处理

如果 LLM 未在回答中使用 `[1]`、`[2]` 等引用标记，则将所有检索结果作为 references 返回，由前端决定如何展示。

---

## 5. 配置项

```yaml
rag:
  post-processing:
    content-summary-length: 200       # 引用来源的内容摘要长度
    include-all-refs-on-no-citation: true  # LLM 未标注引用时是否返回全部 ref
    sensitive-filter-enabled: false    # 敏感词过滤（V2 启用）
    sensitive-words-path: classpath:sensitive-words.txt  # 敏感词表路径
```

---

## 6. 异常处理

| 异常场景 | 处理策略 |
|---------|---------|
| rawAnswer 为 null 或空 | 设置 answer 为"抱歉，未能生成有效回答"，references 为空列表 |
| references 为 null 或空 | answer 正常返回，references 设为空列表 |
| 引用编号越界（如 `[10]` 但仅有 5 条结果） | 跳过越界引用，不报错 |
| 内容截取异常 | 返回完整 content，不截取 |

---

## 7. 演进规划

| 阶段 | 能力 | 说明 |
|------|------|------|
| MVP | 引用提取 + 基础格式化 | 核心功能完整 |
| V1 | 引用链接化 | 将 `[1]` 转为可点击的链接（配合前端） |
| V2 | 敏感词过滤 | 关键词黑名单 + 正则 PII 检测 |
| V2+ | 置信度标记 | 对 LLM 不确定的表述添加标注（如"根据有限资料"） |
| V3 | 全链路 Trace | 在响应中附带 traceId，支持全链路问题排查 |
| V3+ | 反馈收集 | 在响应中附带 feedbackId，支持用户点赞/踩 |
