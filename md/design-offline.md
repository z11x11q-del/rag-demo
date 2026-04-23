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

## 3. 文档生命周期管理（Document Lifecycle）

文档在系统中有完整的生命周期，需要支持新增、更新、删除三种操作，并保证索引数据与源数据的一致性。

### 3.1 文档唯一标识

每份文档入库时生成全局唯一的 `documentId`，并维护如下映射链：

```
documentId → List<chunkId> → List<vectorId>
```

- `documentId`：基于 `数据源类型 + 源路径 + 文件名` 生成（如 SHA256 哈希），确保同一文件幂等
- `chunkId`：`documentId + chunkIndex`，切分后自动生成
- `vectorId`：与 `chunkId` 一一对应，写入向量库时使用

### 3.2 文档去重

在数据源接入阶段进行前置去重：

| 去重维度 | 方式 | 说明 |
|---------|------|------|
| 文件级 | 文件内容 SHA256 哈希 | 相同内容不重复处理 |
| 路径级 | 数据源类型 + 文件路径 | 同一来源的同一文件只保留最新版本 |

```
接入时判断流程：
1. 计算文件 contentHash（SHA256）
2. 查询元数据库是否已存在相同 contentHash
   ├── 存在且未变更 → 跳过，不重复处理
   ├── 存在但内容变更 → 走更新流程
   └── 不存在 → 走新增流程
```

### 3.3 新增流程

```
新文件 → 解析 → 结构化 → 切分 → Embedding → 写入索引
                                              ↓
                                     元数据库记录 document + chunks 映射
```

### 3.4 更新流程

采用**先写新再删旧**策略，避免更新过程中出现检索空窗期：

```
1. 源文件内容变更检测（contentHash 比对）
2. 按新增流程处理新版本（生成新的 chunkIds）
3. 新版本索引写入成功后，删除旧版本的 chunks 和向量
4. 更新元数据库中的 document 记录（版本号 +1）
```

**要点**：
- 更新期间新旧版本短暂共存，不影响在线检索
- 旧版本删除失败时记录补偿任务，后台定时清理

### 3.5 删除流程

```
1. 根据 documentId 查询元数据库，获取所有关联的 chunkIds
2. 批量删除向量索引中对应的 vectorIds
3. 批量删除倒排索引中对应的 chunkIds
4. 删除元数据库中的 document 和 chunk 记录
5. 标记删除状态，异步确认各存储清理完成
```

**要点**：
- 删除操作采用**软删除 + 异步清理**，先标记 `status=DELETED`，后台任务确认各存储都清理完毕后再物理删除
- 防止误删：支持删除确认 / 回收站机制（可选）

---

## 4. 文档解析与清洗层（Parsing & Cleaning Layer）

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

## 5. 文档结构化处理层（Structuring Layer）

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

## 6. Chunk 切分模块（Chunking Module）

将长文档切分为适合向量化的文本块，是 RAG 质量的核心环节。

### 6.1 切分策略

| 切分策略 | 适用场景 | 说明 |
|---------|---------|------|
| 固定长度切分 | 通用场景 | 按 token/字符数切分，配置 overlap |
| 语义切分 | 高质量要求 | 基于句子边界、段落边界切分 |
| 结构感知切分 | 技术文档 | 按章节标题切分，保留上下文层级 |
| 递归切分 | 混合场景 | 先按大边界切，过长再递归细切 |

### 6.2 参数配置

| 参数 | 默认值 | 说明 |
|-----|-------|------|
| `chunk_size` | 512 tokens | 单个 chunk 的目标长度 |
| `chunk_overlap` | 64 tokens（约 12.5%） | 相邻 chunk 重叠区域长度 |
| `min_chunk_size` | 100 tokens | 过短的 chunk 合并到前一个 |
| `max_chunk_size` | 1024 tokens | 超长 chunk 强制再切分 |
| `separator_priority` | `\n\n` > `\n` > `.` > ` ` | 切分时优先使用的分隔符顺序 |

### 6.3 数据源与策略映射

| 数据源类型 | 推荐策略 | chunk_size | 说明 |
|-----------|---------|-----------|------|
| PDF / Word 文档 | 结构感知切分 | 512 tokens | 按标题章节切分，保留层级 |
| Markdown / Wiki | 结构感知切分 | 512 tokens | 直接利用标题语法 |
| FAQ / 问答对 | 固定长度切分 | 256 tokens | 问答对通常较短 |
| 日志 / 纯文本 | 递归切分 | 512 tokens | 无明确结构，递归处理 |
| 数据库记录 | 行级切分 | 不适用 | 每条记录作为独立 chunk |

### 6.4 设计要点

- 配置化切分策略，支持按数据源类型自动选择不同策略和参数
- overlap 控制：相邻 chunk 间保留重叠避免语义断裂，但不宜过大（建议 10%-20%）
- 每个 chunk 继承父文档元数据，并生成唯一 chunkId
- chunk 内附加上下文标题（parent title injection）：将所属章节标题拼接到 chunk 头部，提升检索时的语义完整性

---

## 7. Embedding 生成层（Embedding Layer）

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

## 8. 索引存储层（Index Storage Layer）

### 8.1 稠密向量索引（Dense Index）

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

### 8.2 稀疏向量索引（Sparse Index）

基于词频的倒排索引，补充稠密向量的词汇匹配能力。

- **选型**：ElasticSearch、Lucene（本地）、Milvus Hybrid Search
- **作用**：精准匹配关键词、专业术语、ID 类查询

### 8.3 混合索引写入一致性

同一文档需要同时写入 Dense 索引和 Sparse 索引，双写场景下需要保证数据一致性。

**写入策略：顺序写入 + 补偿机制**

```
1. 写入元数据库（chunk 记录，status=INDEXING）
2. 写入 Dense 向量索引
3. 写入 Sparse 倒排索引
4. 全部成功 → 更新元数据库 status=ACTIVE
5. 任一失败 → 标记 status=INDEX_PARTIAL，进入补偿队列
```

**补偿机制**：
- 后台定时扫描 `status=INDEX_PARTIAL` 的记录
- 检查各存储的实际写入状态，补写缺失的索引
- 超过最大重试次数（如 3 次）后标记 `status=INDEX_FAILED`，触发告警

**一致性保证**：
- 元数据库作为**事实源（Source of Truth）**，记录每个 chunk 在各存储的写入状态
- 在线检索时只查询 `status=ACTIVE` 的数据，避免返回不完整的结果
- 定时对账任务：比对元数据库与各索引存储的数据量，发现不一致自动修复

---

## 9. 元数据存储层（Metadata Storage Layer）

元数据库是整个离线流水线的事实源，存储文档、chunk、任务的全部信息，为在线检索时提供原文回查能力。

### 9.1 存储选型

| 选型 | 特点 | 推荐场景 |
|-----|------|---------|
| PostgreSQL | 关系型、事务强、生态成熟 | 通用推荐（MVP 首选） |
| MySQL | 关系型、运维成熟 | 已有 MySQL 基础设施 |
| MongoDB | 文档型、schema 灵活 | 元数据结构变化频繁 |

**推荐 PostgreSQL**：事务一致性好，配合 pgvector 可一库兼顾元数据和向量存储（MVP 阶段可简化架构）。

### 9.2 职责

| 存什么 | 说明 |
|-------|------|
| 文档记录 | documentId、来源、文件名、contentHash、状态、版本号 |
| Chunk 记录 | chunkId、所属 documentId、原始文本、元数据、在各索引的写入状态 |
| 任务记录 | 任务 ID、阶段、状态、进度、错误信息 |
| 映射关系 | document → chunks → vectors 的关联关系 |

### 9.3 核心作用

- **原文回查**：在线检索召回 chunkId 后，从元数据库查出 chunk 原文、来源、章节等信息，拼入上下文
- **生命周期管理**：文档更新/删除时，通过映射关系级联操作各索引
- **任务状态持久化**：支持断点续传和任务进度查询
- **对账基准**：作为各索引存储的对账基准，发现数据不一致时以元数据库为准

---

## 10. 核心数据模型定义

### 10.1 Document（文档）

| 字段 | 类型 | 说明 |
|-----|------|------|
| `id` | Long | 主键 |
| `document_id` | String | 业务唯一标识（SHA256 生成） |
| `source_type` | String | 数据源类型：FILE / URL / DB / API |
| `source_path` | String | 源路径（文件路径 / URL / 表名） |
| `file_name` | String | 文件名 |
| `content_hash` | String | 文件内容 SHA256 哈希，用于去重和变更检测 |
| `version` | Integer | 文档版本号，更新时自增 |
| `chunk_count` | Integer | 关联的 chunk 数量 |
| `status` | String | PENDING / PROCESSING / ACTIVE / DELETED / FAILED |
| `error_message` | String | 失败时的错误信息 |
| `created_at` | Timestamp | 创建时间 |
| `updated_at` | Timestamp | 最后更新时间 |

### 10.2 Chunk（文本块）

| 字段 | 类型 | 说明 |
|-----|------|------|
| `id` | Long | 主键 |
| `chunk_id` | String | 业务唯一标识（documentId + chunkIndex） |
| `document_id` | String | 所属文档的 documentId |
| `chunk_index` | Integer | 在文档中的序号（从 0 开始） |
| `content` | Text | chunk 原始文本内容 |
| `title_path` | String | 所属章节标题路径（如 "安装指南 > 环境准备"） |
| `metadata` | JSON | 扩展元数据（页码、作者、标签等） |
| `token_count` | Integer | chunk 的 token 数 |
| `embedding_model` | String | 生成向量所用的模型名称及版本 |
| `dense_index_status` | String | 向量索引写入状态：PENDING / SUCCESS / FAILED |
| `sparse_index_status` | String | 倒排索引写入状态：PENDING / SUCCESS / FAILED |
| `status` | String | ACTIVE / DELETED |
| `created_at` | Timestamp | 创建时间 |

### 10.3 IngestionTask（索引任务）

| 字段 | 类型 | 说明 |
|-----|------|------|
| `id` | Long | 主键 |
| `task_id` | String | 任务唯一标识（UUID） |
| `document_id` | String | 关联的文档 documentId |
| `task_type` | String | CREATE / UPDATE / DELETE |
| `stage` | String | 当前阶段：PARSING / STRUCTURING / CHUNKING / EMBEDDING / INDEXING |
| `status` | String | PENDING / RUNNING / SUCCESS / FAILED / CANCELLED |
| `progress` | Integer | 进度百分比（0-100） |
| `total_chunks` | Integer | 总 chunk 数 |
| `processed_chunks` | Integer | 已处理 chunk 数 |
| `retry_count` | Integer | 已重试次数 |
| `max_retries` | Integer | 最大重试次数（默认 3） |
| `error_message` | String | 失败时的错误信息 |
| `started_at` | Timestamp | 任务开始时间 |
| `finished_at` | Timestamp | 任务结束时间 |
| `created_at` | Timestamp | 创建时间 |

### 10.4 ER 关系

```
Document (1) ──── (N) Chunk
    │                    │
    │                    ├── dense_index_status  → 对应 Vector Store 中的记录
    │                    └── sparse_index_status → 对应 BM25 Store 中的记录
    │
    └──── (N) IngestionTask
```

---

## 11. 任务编排与状态管理（Task Orchestration）

### 11.1 任务状态机

```
PENDING → RUNNING → SUCCESS
                  ↘ FAILED → (重试) → RUNNING
                                    → (超过最大重试) → FAILED（终态）
         → CANCELLED（手动取消）
```

### 11.2 阶段流转

每个 IngestionTask 按以下阶段顺序执行，每完成一个阶段更新 `stage` 字段：

```
PARSING → STRUCTURING → CHUNKING → EMBEDDING → INDEXING → DONE
```

**断点续传**：任务失败后重试时，从上次失败的 `stage` 开始，跳过已完成的阶段。

### 11.3 任务编排设计

| 设计项 | 方案 | 说明 |
|-------|------|------|
| 调度框架 | Spring `@Async` + `ThreadPoolTaskExecutor` | MVP 阶段足够；后续可升级为 XXL-Job / Quartz |
| 并发控制 | 线程池核心线程数 = 4，最大线程数 = 8 | 同时处理的文档数上限，避免资源耗尽 |
| 任务队列 | 数据库轮询 / Redis List | 从 task 表中按 PENDING 状态拉取任务 |
| 锁机制 | 乐观锁（version 字段）或分布式锁 | 防止同一任务被多个线程重复处理 |
| 超时控制 | 单文档处理超时 5 分钟 | 超时后标记 FAILED，进入重试 |

### 11.4 重试策略

| 参数 | 默认值 | 说明 |
|-----|-------|------|
| `max_retries` | 3 | 最大重试次数 |
| `retry_interval` | 指数退避：30s → 60s → 120s | 重试间隔递增 |
| 可重试异常 | 网络超时、Embedding 服务不可用、索引写入超时 | 自动重试 |
| 不可重试异常 | 文件格式不支持、解析报错（内容损坏） | 直接标记 FAILED，进入死信 |

### 11.5 任务查询 API

| 接口 | 方法 | 说明 |
|-----|------|------|
| `/api/tasks` | GET | 查询任务列表（支持状态过滤、分页） |
| `/api/tasks/{taskId}` | GET | 查询单个任务详情（阶段、进度、错误） |
| `/api/tasks/{taskId}/retry` | POST | 手动重试失败任务 |
| `/api/tasks/{taskId}/cancel` | POST | 取消运行中/待执行任务 |

---

## 12. 离线数据流完整示意

```
用户上传文件 / 定时任务触发
    ↓
数据源接入（DataSource）
    ↓
文档去重（contentHash 比对）
    ├── 已存在且未变更 → 跳过
    ├── 已存在但变更 → 创建 UPDATE 任务
    └── 不存在 → 创建 CREATE 任务
    ↓
创建 IngestionTask（status=PENDING）
    ↓
任务调度拉取（stage=PARSING）
    ↓
文档解析（Parser）→ 原始文本
    ↓ stage=STRUCTURING
结构化处理（Structurer）→ 段落 + 标题 + 元数据
    ↓ stage=CHUNKING
Chunk 切分（Chunker）→ 文本块列表 → 写入元数据库
    ↓ stage=EMBEDDING
Embedding 生成（EmbeddingClient）→ 向量列表
    ↓ stage=INDEXING
顺序写入（带补偿）：
    1. 写入 Dense 向量索引 → 更新 dense_index_status
    2. 写入 Sparse 倒排索引 → 更新 sparse_index_status
    3. 全部成功 → chunk status=ACTIVE，document status=ACTIVE
    ↓
任务完成（task status=SUCCESS）
```

---

## 13. 接口规划

| 模块 | 核心接口 | 职责 |
|-----|---------|------|
| 数据接入 | `DataSource.ingest()` | 触发数据接入任务 |
| 去重判断 | `DocumentService.checkDuplicate()` | 文件 hash 去重 |
| 解析 | `DocumentParser.parse()` | 原始文件 → 结构化文档 |
| 切分 | `Chunker.split()` | 文档 → chunk 列表 |
| 向量化 | `EmbeddingClient.embed()` | 文本 → 向量 |
| 向量存储 | `VectorStore.upsert() / delete()` | 写入/删除向量索引 |
| 倒排存储 | `BM25Store.index() / delete()` | 写入/删除倒排索引 |
| 元数据管理 | `MetadataStore.save() / query() / delete()` | 文档和 chunk 元数据 CRUD |
| 任务管理 | `TaskService.submit() / query() / retry()` | 任务提交、查询、重试 |
| 流水线编排 | `IngestionPipeline.run()` | 编排完整离线流程 |
| 生命周期 | `DocumentLifecycle.update() / delete()` | 文档更新和删除 |

---

## 14. 非功能性设计

| 指标 | 目标值 | 说明 |
|-----|-------|------|
| 索引速度 | > 1000 chunks/min | 离线处理吞吐 |
| 幂等写入 | 相同文档重复处理不产生重复数据 | 基于 contentHash + chunkId 去重 |
| 断点续传 | 失败后从上次成功的 stage 继续 | 任务状态持久化到元数据库 |
| 增量更新 | 仅处理新增/变更的文档 | 基于 contentHash 判断 |
| 索引一致性 | Dense + Sparse 双写最终一致 | 补偿机制 + 定时对账 |

**可靠性**：
- 索引构建失败支持断点续传、幂等写入
- 解析失败进入死信队列，支持人工介入重试
- 双写失败通过补偿队列自动修复，超过重试上限触发告警
- 定时对账任务：比对元数据库与各索引存储，发现不一致自动修复

**监控告警**：
- 任务失败率 > 5% 触发告警
- 索引写入延迟 > 10s 触发告警
- 补偿队列堆积 > 100 条触发告警
- 对账发现不一致数量 > 10 条触发告警
