# RAG 离线索引流水线技术方案（Ingestion Pipeline）

## 1. 概述

离线索引流水线负责将多种数据源的原始数据，经过解析、清洗、结构化、切分、向量化后写入索引存储，为在线检索提供基础数据支撑。

```
数据源 → 解析清洗 → 结构化 → Chunk切分 → Embedding → 向量索引 + 倒排索引
```

---

## 2. 数据源层（Data Source Layer）

| 数据源类型 | 说明 | 接入方式 |
|-----------|------|---------|
| 本地文件 | PDF、Word、Markdown、TXT | 文件上传 API / 本地扫描 |
| 网页/Wiki | 知识库、Confluence、语雀 | 爬虫 / API 拉取 |
| 数据库 | 结构化知识、FAQ | JDBC / ORM 读取 |
| API | 第三方系统接口 | HTTP Client 定时同步 |
| 日志 | 运行日志、用户反馈 | Filebeat / 日志采集 |

**设计要点**：
- 定义统一的数据源抽象接口 `DataSource`，支持插件化扩展
- 每种数据源独立配置连接参数、同步策略（全量/增量）
- 接入任务通过异步队列（如 Spring 异步线程池 / 调度框架）执行，避免阻塞主线程

---

## 3. 文档解析与清洗层（Parsing & Cleaning Layer）

负责将原始数据转换为纯文本或半结构化格式。

| 处理环节 | 功能 | 技术选型参考 |
|---------|------|------------|
| 格式解析 | PDF 提取、Office 解析、HTML 清洗 | Apache Tika、PDFBox、Jsoup |
| OCR 识别 | 扫描版 PDF / 图片文字提取 | Tesseract、PaddleOCR（外部服务） |
| 去噪清洗 | 去除页眉页脚、重复内容、特殊符号 | 正则 + 规则引擎 |
| 标准化 | 统一编码、空格规范化、全半角转换 | 文本预处理工具 |

**设计要点**：
- 解析结果统一封装为 `ParsedDocument`，包含：原始内容、元数据（来源、时间、作者）、文件类型
- 解析失败进入死信队列，支持人工介入重试

---

## 4. 文档结构化处理层（Structuring Layer）

在纯文本基础上提取语义结构，辅助后续切分质量。

- **标题提取**：基于字体大小（PDF）、Markdown 语法、HTML 标签识别层级标题
- **段落划分**：按自然段落分割，保留段落间关系
- **元数据标注**：来源文件、章节路径、创建时间、数据类型等

**输出结构**：
```
StructuredDocument
├── metadata: Map<String, Object>
├── sections: List<Section>
│   ├── title: String
│   ├── level: int
│   ├── content: String
│   └── children: List<Section>
```

---

## 5. Chunk 切分模块（Chunking Module）

将长文档切分为适合向量化的文本块，是 RAG 质量的核心环节。

| 切分策略 | 适用场景 | 说明 |
|---------|---------|------|
| 固定长度切分 | 通用场景 | 按 token/字符数切分，配置 overlap |
| 语义切分 | 高质量要求 | 基于句子边界、段落边界切分 |
| 结构感知切分 | 技术文档 | 按章节标题切分，保留上下文层级 |
| 递归切分 | 混合场景 | 先按大边界切，过长再递归细切 |

**设计要点**：
- 配置化切分策略，支持按数据源类型选择不同策略
- overlap 控制：相邻 chunk 间保留一定比例重叠，避免语义断裂
- 每个 chunk 继承父文档元数据，并生成唯一 chunkId

---

## 6. Embedding 生成层（Embedding Layer）

将文本 chunk 转化为稠密向量表示。

- **模型选型**：
  - 中文场景：BGE-M3、bge-large-zh、text2vec
  - 多语言场景：BGE-M3（支持 dense + sparse + colbert）
  - 英文场景：text-embedding-3、BAAI/bge-en
- **调用方式**：
  - 本地部署：通过 ONNX / TensorRT 本地推理（推荐 Java ONNX Runtime）
  - 远程服务：调用 Python 推理服务（FastAPI / Triton）

**设计要点**：
- 封装 `EmbeddingClient` 接口，屏蔽本地/远程差异
- 批量推理提升吞吐，配置 batchSize 和超时参数
- 向量维度、模型版本作为配置项，支持热切换

---

## 7. 索引存储层（Index Storage Layer）

### 7.1 稠密向量索引（Dense Index）

存储 Embedding 向量，支持相似度检索。

| 选型 | 特点 | 适用规模 |
|-----|------|---------|
| Milvus | 分布式、功能丰富、云原生 | 大规模生产环境 |
| Qdrant | 轻量、高性能、易部署 | 中小规模 / 私有化 |
| pgvector | PostgreSQL 扩展，事务一致 | 已有 PG 基础设施 |
| FAISS | 纯内存、极致性能 | 中小规模、实验环境 |

**设计要点**：
- 按 collection 隔离不同业务/租户数据
- 支持 HNSW、IVF 等索引算法，按数据规模自动选择
- 向量 + 标量混合过滤（如按数据源类型、时间范围过滤）

### 7.2 稀疏向量索引（Sparse Index）

基于词频的倒排索引，补充稠密向量的词汇匹配能力。

- **选型**：ElasticSearch、Lucene（本地）、Milvus Hybrid Search
- **作用**：精准匹配关键词、专业术语、ID 类查询

### 7.3 混合索引策略

- 同一文档同时写入 Dense 和 Sparse 索引
- 检索时双路并发查询，结果融合

---

## 8. 离线数据流完整示意

```
用户上传文件 / 定时任务触发
    ↓
数据源接入（DataSource）
    ↓
文档解析（Parser）→ 原始文本
    ↓
结构化处理（Structurer）→ 段落 + 标题 + 元数据
    ↓
Chunk 切分（Chunker）→ 文本块列表
    ↓
Embedding 生成（EmbeddingClient）→ 向量列表
    ↓
并行写入：
    ├── 稠密向量索引（Vector Store）
    └── 稀疏倒排索引（BM25 Store）
```

---

## 9. 接口规划

| 模块 | 核心接口 | 职责 |
|-----|---------|------|
| 数据接入 | `DataSource.ingest()` | 触发数据接入任务 |
| 解析 | `DocumentParser.parse()` | 原始文件 → 结构化文档 |
| 切分 | `Chunker.split()` | 文档 → chunk 列表 |
| 向量化 | `EmbeddingClient.embed()` | 文本 → 向量 |
| 向量存储 | `VectorStore.upsert()` | 写入向量索引 |
| 倒排存储 | `BM25Store.index()` | 写入倒排索引 |
| 流水线编排 | `IngestionPipeline.run()` | 编排完整离线流程 |

---

## 10. 非功能性设计

| 指标 | 目标值 | 说明 |
|-----|-------|------|
| 索引速度 | > 1000 chunks/min | 离线处理吞吐 |
| 幂等写入 | 相同文档重复处理不产生重复数据 | 基于 chunkId 去重 |
| 断点续传 | 失败后从上次成功位置继续 | 任务状态持久化 |
| 增量更新 | 仅处理新增/变更的文档 | 基于文件 hash 或时间戳判断 |

**可靠性**：
- 索引构建失败支持断点续传、幂等写入
- 解析失败进入死信队列，支持人工介入重试
- 索引监控：数据量、索引健康度、写入延迟
