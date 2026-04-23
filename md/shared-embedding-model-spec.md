# Embedding 模型规格 — text-embedding-v4（公共参考文档）

> 本文档为公共参考文档，供离线索引流水线（Ingestion Pipeline）和在线检索流水线（Retrieval Pipeline）共同引用。

---

## 1. 模型概览

使用阿里云 DashScope 的 **text-embedding-v4** 模型，通过 OpenAI 兼容接口调用。

| 参数 | 值 |
|-----|-----|
| 模型名称 | `text-embedding-v4` |
| 最大输入长度 | 8,192 tokens / 条 |
| 输出向量维度 | 可选：2048 / 1536 / **1024（默认）** / 768 / 512 / 256 / 128 / 64 |
| 支持语种 | 100+ 种（中英日韩法德俄等） |
| 批次大小 | 单次最多 **10 条**文本 |
| 向量类型 | `dense` / `sparse` / `dense&sparse`（混合模式，成本不变） |
| 计费 | 0.0005 元 / 千 Token（Batch 接口减半） |
| 免费额度 | 100 万 Token（开通后 90 天内） |

---

## 2. API 调用规范

### 2.1 接入地址

```
POST https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings
```

### 2.2 认证方式

```
Authorization: Bearer $DASHSCOPE_API_KEY
Content-Type: application/json
```

### 2.3 请求格式

**单条请求**：

```json
{
    "model": "text-embedding-v4",
    "input": "衣服的质量杠杠的",
    "dimension": 1024
}
```

**批量请求**（最多 10 条）：

```json
{
    "model": "text-embedding-v4",
    "input": ["文本1", "文本2", "文本3"],
    "dimension": 1024
}
```

**可选参数**：

| 参数 | 类型 | 说明 |
|-----|------|------|
| `model` | String | 模型名称，固定 `text-embedding-v4` |
| `input` | String / List\<String\> | 输入文本，批量时最多 10 条 |
| `dimension` | Integer | 输出向量维度，默认 1024 |
| `encoding_format` | String | 向量编码格式：`float`（默认）/ `base64` |

### 2.4 响应格式

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

**响应字段说明**：

| 字段 | 说明 |
|-----|------|
| `data[].embedding` | 稠密向量数组，长度等于 `dimension` |
| `data[].index` | 输入文本的原始序号（批量时用于对应） |
| `usage.prompt_tokens` | 输入文本消耗的 token 数 |
| `usage.total_tokens` | 总消耗 token 数（embedding 场景与 prompt_tokens 相同） |

### 2.5 错误码

| HTTP 状态码 | 错误码 | 说明 | 处理方式 |
|------------|-------|------|---------|
| 400 | InvalidParameter | 参数错误（如输入为空、超长） | 检查输入参数 |
| 401 | Unauthorized | API Key 无效或过期 | 更换 API Key |
| 429 | RateLimitExceeded | 并发限流 | 指数退避重试 |
| 500 | InternalError | 服务端内部错误 | 重试 |
| 503 | ServiceUnavailable | 服务不可用 | 延迟重试 |

---

## 3. text_type 区分（query vs document）

text-embedding-v4 支持通过 `text_type` 参数区分索引和检索场景，优化向量质量：

| 场景 | text_type | 使用时机 |
|-----|-----------|---------|
| 离线索引 | `"document"` | 对文档 chunk 生成 Embedding 时 |
| 在线检索 | `"query"` | 对用户 query 生成 Embedding 时 |

> **说明**：OpenAI 兼容接口中，`text_type` 需通过额外参数传递。不同 text_type 下生成的向量在同一空间中，query 向量与 document 向量可直接计算相似度。

---

## 4. dense&sparse 混合向量

text-embedding-v4 支持 `output_type: "dense&sparse"` 一次调用同时返回稠密和稀疏向量：

```json
{
    "model": "text-embedding-v4",
    "input": "示例文本",
    "dimension": 1024,
    "output_type": "dense&sparse"
}
```

**混合响应**包含：
- `dense_embedding`：稠密向量（用于语义检索）
- `sparse_embedding`：稀疏向量（用于关键词检索）

> **演进建议**：后续可考虑用此能力替代独立的 BM25 倒排索引，简化架构，降低运维成本。

---

## 5. 对系统各模块的约束

### 5.1 对 Chunker 的约束

- `maxChunkSize` 上限为 8192 tokens，当前配置 1024 tokens 留有充足余量
- 语义切分时利用 `embedBatch` 批量计算句子向量，单次最多 10 条，需分批调用

### 5.2 对 EmbeddingClient 的约束

- 批量调用时需按 **10 条/批** 分片，内部循环调用
- 需处理限流（429）和服务不可用（503），实现重试机制
- 向量维度需与 VectorStore 的索引维度配置一致

### 5.3 对 VectorStore 的约束

- 索引维度需与 Embedding 模型的 `dimension` 参数一致（默认 1024）
- 相似度算法推荐 **余弦相似度（Cosine Similarity）**，text-embedding-v4 输出已归一化

### 5.4 对 RetrievalService 的约束

- 在线查询时 `text_type` 必须传 `"query"`，与离线索引的 `"document"` 对应
- Query Embedding 为单条调用，延迟敏感，需配置较短超时（如 3s）
