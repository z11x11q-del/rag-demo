# 上下文构造模块详细设计（Context Builder）

## 1. 模块职责

将重排后的检索结果组织为 LLM 可消费的结构化上下文文本，包括去重、排序、Token 截断、编号和元数据注入。位于重排之后、Prompt 构建之前。

---

## 2. 接口定义

### 2.1 ContextBuilder 接口

```java
package com.example.ragdemo.retrieval;

public interface ContextBuilder {

    /**
     * 构建上下文文本
     *
     * @param results 重排后的检索结果
     * @return 格式化后的上下文字符串（包含编号、来源、内容）
     */
    String build(List<RetrievalResult> results);
}
```

### 2.2 输入/输出说明

| 方向 | 类型 | 说明 |
|------|------|------|
| 输入 | `List<RetrievalResult>` | 重排后的 TopN 结果，已按相关性降序排列 |
| 输出 | `String` | 格式化后的上下文字符串，直接嵌入 Prompt |

### 2.3 RetrievalResult 可用字段

| 字段 | 类型 | 用途 |
|------|------|------|
| `chunkId` | String | chunk 唯一标识 |
| `content` | String | 文本内容 |
| `fileName` | String | 来源文件名 |
| `titlePath` | String | 章节路径（如 "安装指南 > 环境准备"） |
| `score` | double | 相关性分数 |

---

## 3. 详细设计

### 3.1 处理流程

```
List<RetrievalResult>（TopN）
    │
    ▼
┌──────────────┐
│  Step 1: 去重 │  按 chunkId 去重，保留分数高的
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  Step 2: 排序 │  按相关性分数降序排列
└──────┬───────┘
       │
       ▼
┌──────────────────┐
│  Step 3: Token   │  累加 token 数，超过预算则截断
│         截断     │
└──────┬───────────┘
       │
       ▼
┌──────────────────────┐
│  Step 4: 编号+格式化  │  为每个 chunk 分配序号，注入元数据
└──────┬───────────────┘
       │
       ▼
    String（上下文文本）
```

### 3.2 Step 1 — 去重

按 `chunkId` 去重。如果同一个 chunk 从多条通道被召回（Dense + Sparse），保留分数最高的那条：

```java
private List<RetrievalResult> deduplicate(List<RetrievalResult> results) {
    Map<String, RetrievalResult> seen = new LinkedHashMap<>();
    for (RetrievalResult r : results) {
        seen.merge(r.getChunkId(), r,
            (existing, incoming) ->
                incoming.getScore() > existing.getScore() ? incoming : existing);
    }
    return new ArrayList<>(seen.values());
}
```

### 3.3 Step 2 — 排序

去重后按分数降序重排：

```java
results.sort(Comparator.comparingDouble(RetrievalResult::getScore).reversed());
```

### 3.4 Step 3 — Token 截断

LLM 的上下文窗口有限，需要控制上下文总 token 数不超过预算：

```java
private List<RetrievalResult> truncateByTokenBudget(
        List<RetrievalResult> results, int tokenBudget) {
    List<RetrievalResult> truncated = new ArrayList<>();
    int usedTokens = 0;

    for (RetrievalResult r : results) {
        int chunkTokens = estimateTokens(r.getContent());
        int metaTokens = estimateTokens(formatMeta(r));  // 编号+来源的 token

        if (usedTokens + chunkTokens + metaTokens > tokenBudget) {
            break;  // 超出预算，停止添加
        }

        truncated.add(r);
        usedTokens += chunkTokens + metaTokens;
    }

    return truncated;
}
```

Token 估算方式：复用 `ApproximateTokenCounter`（已有实现），中英文混合文本按 `字符数 / 1.5` 近似。

### 3.5 Step 4 — 编号 + 格式化

为每个 chunk 分配编号，注入来源元数据，生成最终上下文字符串：

**上下文模板：**

```
[1] 来源：{fileName} | 章节：{titlePath}
{content}

[2] 来源：{fileName} | 章节：{titlePath}
{content}

...
```

**实现：**

```java
private String format(List<RetrievalResult> results) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < results.size(); i++) {
        RetrievalResult r = results.get(i);
        sb.append("[").append(i + 1).append("] ");

        // 来源信息（有则展示）
        if (r.getFileName() != null || r.getTitlePath() != null) {
            sb.append("来源：");
            if (r.getFileName() != null) sb.append(r.getFileName());
            if (r.getTitlePath() != null) sb.append(" | 章节：").append(r.getTitlePath());
            sb.append("\n");
        }

        sb.append(r.getContent()).append("\n\n");
    }
    return sb.toString().strip();
}
```

**输出示例：**

```
[1] 来源：产品手册_v2.3.pdf | 章节：安装指南 > 环境准备
首先下载安装包，然后执行 setup.exe，按照向导完成安装。

[2] 来源：FAQ数据库 | 章节：常见问题
如果安装失败，请检查系统版本是否满足最低要求（Windows 10 或以上）。
```

### 3.6 空结果处理

当 results 为空时，返回特殊标记文本，提示 LLM 没有检索到相关信息：

```java
private static final String EMPTY_CONTEXT = "（未检索到相关参考文档）";
```

---

## 4. MVP 实现方案

### 4.1 实现类：DefaultContextBuilder

```java
@Slf4j
@RequiredArgsConstructor
@Component
public class DefaultContextBuilder implements ContextBuilder {

    private static final String EMPTY_CONTEXT = "（未检索到相关参考文档）";
    private static final int DEFAULT_TOKEN_BUDGET = 3000;

    @Override
    public String build(List<RetrievalResult> results) {
        if (results == null || results.isEmpty()) {
            return EMPTY_CONTEXT;
        }

        // Step 1: 去重
        List<RetrievalResult> deduped = deduplicate(results);

        // Step 2: 排序（去重后可能打乱顺序）
        deduped.sort(Comparator.comparingDouble(
            RetrievalResult::getScore).reversed());

        // Step 3: Token 截断
        List<RetrievalResult> truncated =
            truncateByTokenBudget(deduped, DEFAULT_TOKEN_BUDGET);

        // Step 4: 编号 + 格式化
        return format(truncated);
    }
}
```

---

## 5. 配置项

```yaml
rag:
  context:
    token-budget: 3000              # 上下文 token 预算
    max-chunks: 10                  # 最多包含的 chunk 数量
    show-source: true               # 是否在上下文中展示来源信息
    show-score: false               # 是否在上下文中展示分数（调试用）
    empty-context-text: "（未检索到相关参考文档）"
```

---

## 6. 异常处理

| 异常场景 | 处理策略 |
|---------|---------|
| results 为 null 或空 | 返回 `EMPTY_CONTEXT` 常量字符串 |
| RetrievalResult.content 为 null | 跳过该条结果，记录 warn 日志 |
| Token 估算异常 | 降级为固定 chunk 数量截断（取前 `max-chunks` 条） |

---

## 7. 演进规划

| 阶段 | 能力 | 说明 |
|------|------|------|
| MVP | 去重 + 排序 + Token 截断 + 编号格式化 | 核心能力完整 |
| V1 | 配置化 Token 预算 | 从 application.yml 读取预算值 |
| V2 | 智能截断 | 按句子边界截断长 chunk，而非整条丢弃 |
| V2+ | 上下文压缩 | 对长文档做 LLM 摘要后再注入上下文 |
| V3 | 多轮对话上下文 | 支持将历史对话上下文拼接到当前上下文中 |
