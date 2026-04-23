# Chunker Split 详细设计文档

## 1. 模块定位

`Chunker.split(StructuredDocument)` 是离线索引流水线中**承上启下的核心环节**：上游接收结构化文档（含章节层级、元数据），下游输出适合 Embedding 的文本块列表。切分质量直接决定了检索召回率和 LLM 生成质量。

```
Structurer 输出                Chunker                   Embedding 输入
StructuredDocument  ──→  split(document)  ──→  List<TextChunk>
  ├── metadata                                    ├── index
  └── sections[]                                  ├── content
       ├── title                                  ├── titlePath
       ├── level                                  └── tokenCount
       ├── content
       └── children[]
```

---

## 2. 技术选型决策

### 2.1 Embedding 模型：阿里云 text-embedding-v4

使用阿里云 DashScope 的 **text-embedding-v4** 模型，通过 OpenAI 兼容接口调用。

**模型关键参数**：

| 参数 | 值 |
|-----|-----|
| 模型名称 | `text-embedding-v4` |
| 最大输入长度 | 8,192 tokens / 条 |
| 输出向量维度 | 可选：2048 / 1536 / **1024（默认）** / 768 / 512 / 256 / 128 / 64 |
| 支持语种 | 100+ 种（中英日韩法德俄等） |
| 批次大小 | 单次最多 10 条文本 |
| 向量类型 | `dense` / `sparse` / `dense&sparse`（混合模式，成本不变） |
| 计费 | 0.0005 元 / 千 Token（Batch 接口减半） |
| 免费额度 | 100 万 Token（开通后 90 天内） |

**API 调用方式**（OpenAI 兼容接口）：

```bash
curl --location 'https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings' \
--header "Authorization: Bearer $DASHSCOPE_API_KEY" \
--header 'Content-Type: application/json' \
--data '{
    "model": "text-embedding-v4",
    "input": "衣服的质量杠杠的",
    "dimension": 1024
}'
```

**响应格式**：

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "embedding": [0.123, 0.456, ...],
      "index": 0
    }
  ],
  "model": "text-embedding-v4",
  "usage": {
    "prompt_tokens": 10,
    "total_tokens": 10
  }
}
```

**对 Chunker 的约束**：
- `maxChunkSize` 上限为 8192 tokens，当前配置 1024 tokens 留有充足余量
- 语义切分时利用 `embedBatch` 批量计算句子向量，单次最多 10 条，需分批调用
- 支持 `text_type` 区分 query / document，索引阶段应传 `"document"`，在线检索传 `"query"`

**特别说明 — dense&sparse 混合向量**：
text-embedding-v4 支持 `output_type: "dense&sparse"` 一次调用同时返回稠密和稀疏向量，后续可考虑用此能力替代独立的 BM25 倒排索引，简化架构。

### 2.2 Token 计算方案

采用 **Java 库近似估算**方案，后续切换为远程精确计算。

**当前方案（一期）**：

```java
public class ApproximateTokenCounter implements TokenCounter {
    /**
     * 近似估算 token 数
     * - 中文：约 1.5 字符 / token（基于 BPE 分词特性）
     * - 英文：约 4 字符 / token
     * - 混合文本取加权计算
     */
    @Override
    public int countTokens(String text) {
        int cjk = countCjkChars(text);
        int other = text.length() - cjk;
        return (int) Math.ceil(cjk / 1.5 + other / 4.0);
    }
}
```

**误差范围**：近似估算与实际 token 数偏差约 ±15%，对切分过程可接受。

> **TODO**：后续对接远程 tokenizer 服务（或利用 DashScope 响应中的 `usage.prompt_tokens` 校准），实现精确 token 计算，替换近似估算。

**演进路径**：

| 阶段 | 方案 | 精度 |
|-----|------|------|
| 一期（当前） | Java 近似估算（字符数 / 系数） | ±15% |
| 二期 | 远程 tokenizer 服务精确计算 | 精确 |
| 可选优化 | 利用 DashScope `usage.prompt_tokens` 回写校准历史数据 | 精确 |

### 2.3 文档来源类型传递

在 `StructuredDocument` 上新增显式字段 `sourceType`，用于切分策略自动选择。

**变更**：

```java
public class StructuredDocument {
    private String sourceType;          // 新增：数据源类型（FILE / URL / DB / API / LOG）
    private Map<String, Object> metadata;
    private List<Section> sections;
}
```

**策略映射**：

| sourceType | 文档子类型 | 推荐策略 | chunk_size |
|-----------|----------|---------|-----------|
| FILE（PDF/Word/Markdown） | 技术文档 | 结构感知 + 语义切分 | 512 |
| FILE（FAQ） | 问答对 | 固定长度 | 256 |
| FILE（法律/合同） | 法律文档 | 结构感知 | 768 |
| URL | 网页/Wiki | 结构感知 | 512 |
| DB | 数据库记录 | 行级切分 | 不适用 |
| LOG | 日志/纯文本 | 递归切分 | 512 |

`sourceType` 由上游 Structurer 在结构化处理时从 `ParsedDocument` 的元数据中提取并设置。

---

## 3. 核心 Trade-off 分析

### 3.1 Chunk Size（切分粒度）

Chunk Size 是影响 RAG 效果最关键的超参数，本质上是**语义完整性 vs 检索精度**的权衡。

| 维度 | 小 Chunk（128-256 tokens） | 大 Chunk（512-1024 tokens） |
|-----|--------------------------|---------------------------|
| 检索精度 | **高** — 粒度细，向量与 query 更对齐 | **低** — 向量被噪声稀释，可能误召回 |
| 语义完整性 | **低** — 容易截断语义，丢失上下文 | **高** — 保留完整论述、因果关系 |
| LLM 上下文利用率 | 可塞入更多 chunk，覆盖更多知识点 | 每个 chunk 占用更多 token 预算 |
| Embedding 质量 | 内容聚焦，向量语义明确 | 内容混杂，向量语义模糊 |
| 存储与索引成本 | chunk 数量多，索引体积大 | chunk 数量少，索引体积小 |
| 延迟 | Embedding 调用次数多 | Embedding 调用次数少 |

**推荐策略**：

| 文档类型 | 推荐 Chunk Size | 理由 |
|---------|----------------|------|
| 技术文档（PDF/Word/Markdown） | 512 tokens | 段落通常 200-500 tokens，512 能容纳完整段落 |
| FAQ / 问答对 | 256 tokens | 问答对短小精悍，过大会混入无关内容 |
| 法律/合同文档 | 768-1024 tokens | 条款之间关联性强，需要更大上下文 |
| 日志/纯文本 | 256-512 tokens | 无结构，按固定窗口切分 |
| 代码文档 | 256-512 tokens | 按函数/类级别切分更合理 |

### 3.2 Chunk Overlap（重叠区域）

Overlap 是**语义连续性 vs 冗余成本**的权衡。

| 维度 | 无 Overlap（0） | 小 Overlap（10-15%） | 大 Overlap（25-50%） |
|-----|----------------|---------------------|---------------------|
| 语义断裂风险 | **高** — 切分边界处信息丢失 | **低** — 跨边界内容有冗余保护 | **极低** |
| 存储冗余 | 无 | 约增加 10-15% | 约增加 25-50% |
| 检索冗余 | 无 | 低概率召回重叠片段 | 高概率召回重复内容，需去重 |
| Embedding 成本 | 最低 | 略增加 | 显著增加 |

**Overlap 大小选择指南**：

```
overlap_ratio = chunk_overlap / chunk_size

推荐范围：10% ~ 20%

- 10%（如 512 / 51）：适合结构清晰的文档（Markdown、有标题的 PDF）
- 12.5%（如 512 / 64）：通用默认值 ← 当前配置
- 20%（如 512 / 102）：适合连续叙述性文本（小说、论文正文）
- > 25%：通常不推荐，冗余过高，检索去重压力大
```

**关键原则**：Overlap 应对齐到**自然语义边界**（句子结尾），而非机械按 token 数截取。

### 3.3 Min/Max Chunk Size（合并与强制切分阈值）

这两个参数处理切分后的**异常情况**：

| 参数 | 作用 | 不设置的后果 |
|-----|------|------------|
| `minChunkSize`（100 tokens） | 过短 chunk 合并到相邻 chunk | 产生大量碎片 chunk，向量语义弱，浪费索引空间 |
| `maxChunkSize`（1024 tokens） | 超长 chunk 强制再切分 | 超出 Embedding 模型最大输入长度，向量质量退化 |

**Max Chunk Size 需与 Embedding 模型对齐**：

| Embedding 模型 | 最大输入 tokens | 建议 maxChunkSize |
|---------------|----------------|-------------------|
| BGE-M3 | 8192 | 1024（留足余量） |
| text-embedding-3-small | 8191 | 1024 |
| bge-large-zh | 512 | 480（严格限制） |
| text2vec-large-chinese | 512 | 480 |

---

## 4. 切分策略设计

### 4.1 策略总览

系统应支持多种切分策略，通过策略模式按文档类型自动选择：

```
                        ┌─ FixedSizeChunker（固定长度）
                        │
Chunker (interface) ────┼─ RecursiveChunker（递归切分）
                        │
                        ├─ StructureAwareChunker（结构感知）
                        │
                        └─ SemanticChunker（语义切分）
```

### 4.2 递归切分（推荐默认策略）

递归切分是最通用的策略，按分隔符优先级逐级尝试：

```
输入: Section.content + 配置参数

分隔符优先级（从粗到细）:
  Level 1: "\n\n"  （段落边界）
  Level 2: "\n"    （行边界）
  Level 3: "。" / ". " （句子边界）
  Level 4: "，" / ", " （子句边界）
  Level 5: " "    （词边界）

算法流程:
  1. 用当前优先级最高的分隔符分割文本
  2. 合并相邻片段，直到接近 chunkSize
  3. 如果某片段 > maxChunkSize，用下一级分隔符递归细切
  4. 如果某片段 < minChunkSize，合并到前一个 chunk
  5. 相邻 chunk 之间保留 chunkOverlap 的重叠
```

**伪代码**：

```java
List<TextChunk> recursiveSplit(String text, List<String> separators, int depth) {
    if (tokenCount(text) <= chunkSize) {
        return List.of(makeChunk(text));
    }

    String sep = separators.get(depth);
    String[] segments = text.split(sep);

    List<TextChunk> chunks = new ArrayList<>();
    StringBuilder buffer = new StringBuilder();

    for (String segment : segments) {
        if (tokenCount(buffer + sep + segment) > chunkSize) {
            // buffer 已满，输出为一个 chunk
            chunks.add(makeChunk(buffer.toString()));
            // 保留 overlap：从 buffer 尾部截取 overlapTokens 个 token
            buffer = new StringBuilder(tailTokens(buffer, chunkOverlap));
        }
        buffer.append(sep).append(segment);
    }

    // 处理剩余内容
    if (tokenCount(buffer) >= minChunkSize) {
        chunks.add(makeChunk(buffer.toString()));
    } else if (!chunks.isEmpty()) {
        // 合并到最后一个 chunk
        mergeToLast(chunks, buffer.toString());
    }

    // 对超长 chunk 递归细切
    return chunks.stream()
        .flatMap(c -> tokenCount(c) > maxChunkSize && depth + 1 < separators.size()
            ? recursiveSplit(c.getContent(), separators, depth + 1).stream()
            : Stream.of(c))
        .toList();
}
```

### 4.3 结构感知切分（技术文档推荐）

利用 `StructuredDocument` 的章节层级信息，优先在章节边界切分：

```
算法流程:
  1. 深度优先遍历 Section 树
  2. 对每个叶子 Section：
     a. 如果 content token 数 ≤ chunkSize → 整体作为一个 chunk
     b. 如果 content token 数 > chunkSize → 对 content 执行递归切分
  3. 对每个非叶子 Section：
     a. 如果所有子 Section 总 token 数 ≤ chunkSize → 合并为一个 chunk
     b. 否则各子 Section 独立处理
  4. 每个 chunk 注入 titlePath（如 "安装指南 > 环境准备 > JDK 安装"）
  5. 过短 chunk（< minChunkSize）尝试与同级相邻 Section 合并
```

**titlePath 注入的价值**：

```
不注入 titlePath 的 chunk:
  "执行 mvn clean install 命令，等待依赖下载完成"
  → 向量语义模糊，不知道在讲什么场景

注入 titlePath 的 chunk:
  "[安装指南 > 环境准备 > Maven 配置] 执行 mvn clean install 命令，等待依赖下载完成"
  → 向量语义明确，检索时能准确匹配 "Maven 安装" 相关 query
```

### 4.4 语义切分（高质量场景）

基于 Embedding 相似度判断语义断点：

```
算法流程:
  1. 按句子分割文本
  2. 计算相邻句子对的 Embedding 余弦相似度
  3. 相似度低于阈值的位置标记为语义断点
  4. 在断点处切分，形成语义连贯的 chunk
  5. 过长 chunk 再用递归策略细切
```

**优势**：切分结果语义最完整
**劣势**：需要额外 Embedding 调用（text-embedding-v4 批次上限 10 条，需分批），离线处理成本增加

---

## 5. Token 计算策略

Chunk 切分以 **token** 为单位而非字符数，因为 Embedding 模型和 LLM 都以 token 为输入单位。

### 5.1 中英文 Token 差异

| 语言 | 1 token ≈ | 说明 |
|-----|-----------|------|
| 英文 | 4 个字符 / 0.75 个单词 | 基于 BPE 分词 |
| 中文 | 1-2 个汉字 | 取决于分词器 |

### 5.2 Token 计算方案

| 方案 | 精度 | 性能 | 推荐场景 |
|-----|------|------|---------|
| 精确计算（调用 tokenizer） | 高 | 低 | 最终校验 |
| 近似估算（字符数 / 系数） | 中 | 高 | 切分过程中快速判断 |
| 混合策略 | 高 | 中 | **推荐**：切分时近似估算，输出时精确校验 |

**推荐实现**：

```java
public interface TokenCounter {
    /** 精确计算 token 数 */
    int countTokens(String text);

    /** 快速估算（用于切分过程） */
    default int estimateTokens(String text) {
        // 中文按 1.5 字符/token，英文按 4 字符/token
        // 混合文本取加权平均
        int cjk = countCjkChars(text);
        int other = text.length() - cjk;
        return (int) (cjk / 1.5 + other / 4.0);
    }
}
```

---

## 6. 边界处理与质量保障

### 6.1 切分边界对齐

切分时应避免在以下位置截断：

| 不应截断的位置 | 处理方式 |
|-------------|---------|
| 句子中间 | 回退到最近的句号/问号/感叹号 |
| 代码块中间 | 回退到代码块开始或结束位置 |
| 表格行中间 | 回退到完整行边界 |
| 列表项中间 | 回退到列表项开头 |
| 括号/引号未配对 | 前移到配对完成的位置 |

### 6.2 特殊内容处理

| 内容类型 | 处理策略 |
|---------|---------|
| 代码块（```...```） | 尽量不切分；超长时按函数/类边界切分 |
| 表格 | 表头 + 当前行作为一个 chunk；超长表格按行分组 |
| 图片引用 | 保留引用标记，附加 alt text 到 chunk |
| 公式 | 不在公式中间切分 |
| 超链接 | 保持链接文本完整 |

### 6.3 Chunk 质量校验

切分完成后，对每个 chunk 执行质量校验：

```
校验规则:
  1. tokenCount ∈ [minChunkSize, maxChunkSize]  // 长度合规
  2. content.trim().length() > 0                // 非空
  3. 非纯标点/空白符                              // 有实际内容
  4. 不是纯重复内容（与前一个 chunk 相似度 < 0.95） // 去重
```

不通过校验的 chunk 处理：
- 过短 → 合并到前一个 chunk
- 过长 → 递归再切分
- 空/无效 → 丢弃并记录日志

---

## 7. 配置化设计

### 7.1 配置参数

```yaml
rag:
  chunking:
    # 基础参数
    chunk-size: 512           # 目标 chunk 大小（tokens）
    chunk-overlap: 64         # 重叠区域大小（tokens）
    min-chunk-size: 100       # 最小 chunk 大小（tokens）
    max-chunk-size: 1024      # 最大 chunk 大小（tokens）

    # 策略选择（扩展）
    default-strategy: RECURSIVE           # 默认切分策略
    separator-priority: "\n\n,\n,。,. ,，,，, "  # 分隔符优先级

    # 按数据源覆盖（扩展）
    overrides:
      FAQ:
        chunk-size: 256
        default-strategy: FIXED_SIZE
      LEGAL:
        chunk-size: 768
        chunk-overlap: 128

    # titlePath 注入
    inject-title-path: true   # 是否将章节标题注入 chunk 头部
    title-path-separator: " > "  # 标题路径分隔符
```

### 7.2 ChunkingProperties 扩展建议

当前 `ChunkingProperties` 包含 4 个基础参数（chunkSize、chunkOverlap、minChunkSize、maxChunkSize），后续可扩展：

| 新增字段 | 类型 | 说明 |
|---------|------|------|
| `defaultStrategy` | String | 默认切分策略名称 |
| `separatorPriority` | List\<String\> | 分隔符优先级列表 |
| `injectTitlePath` | boolean | 是否注入章节标题 |
| `titlePathSeparator` | String | 标题路径分隔符 |
| `overrides` | Map\<String, ChunkingOverride\> | 按数据源类型覆盖参数 |

---

## 8. 与上下游的协作

### 8.1 与 Structurer（上游）的协作

```
Structurer 的输出质量直接影响 Chunker 效果:
  - 章节层级越准确 → 结构感知切分越好
  - 段落划分越清晰 → 递归切分找到更好的边界
  - metadata 越丰富 → chunk 携带的上下文越完整
```

### 8.2 与 Embedding（下游）的协作

```
Chunker 的输出需要适配 Embedding 模型:
  - maxChunkSize 不能超过模型的 max_seq_length
  - chunk 内容应与模型的训练数据分布一致（如不要混入过多特殊符号）
  - titlePath 注入可以提升 Embedding 的语义聚焦度
```

### 8.3 与 Retrieval（在线）的协作

```
切分粒度影响在线检索效果:
  - chunk 过大 → 召回噪声多，LLM context window 利用率低
  - chunk 过小 → 语义不完整，LLM 无法基于碎片信息推理
  - overlap 过大 → 召回结果重复，需要 PostProcessor 去重
```

---

## 9. 性能考量

| 维度 | 目标 | 手段 |
|-----|------|------|
| 吞吐量 | > 1000 chunks/min | 切分是 CPU 密集型，单线程即可满足；瓶颈在 Embedding |
| 内存 | 单文档 < 100MB | 流式遍历 Section 树，不需要全部加载 |
| Token 计算 | 切分过程用近似估算 | 精确计算仅用于最终输出校验 |

---

## 10. 实现路线

一期直接实现完整能力（含语义切分 + 精确 Token 计算），不做分阶段裁剪。

### 10.1 一期实现范围

| 能力 | 说明 |
|-----|------|
| 递归切分 | 按分隔符优先级逐级切分，通用兜底策略 |
| 结构感知切分 | 利用 Section 层级，优先在章节边界切分 + titlePath 注入 |
| 语义切分 | 基于 text-embedding-v4 计算句间相似度，在语义断点处切分 |
| 按数据源自动选策略 | 根据 `StructuredDocument.sourceType` 路由到不同策略和参数 |
| Token 近似估算 | Java 本地近似计算（±15%），满足切分需求 |
| 完整配置化 | 基础参数 + 策略选择 + 按数据源覆盖 + 语义阈值 |

### 10.2 后续 TODO

| 事项 | 优先级 | 说明 |
|-----|-------|------|
| Token 精确计算 | P1 | 对接远程 tokenizer 服务，或利用 DashScope `usage.prompt_tokens` 校准 |
| dense&sparse 混合向量 | P2 | 利用 text-embedding-v4 的 `output_type: "dense&sparse"` 替代独立 BM25 |
| 自适应切分 | P3 | 根据在线召回率反馈，自动优化 chunk_size 和 overlap 参数 |
