# RAG 系统整体技术方案设计

## 1. 项目概述

本项目基于 Spring Boot 3.x + Java 21 构建一套完整的 RAG（Retrieval-Augmented Generation）系统，支持多源数据接入、多路召回、重排序及大模型生成能力。

## 2. 整体架构

系统分为两大流水线：

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           离线索引流水线（Ingestion）                          │
│  数据源 → 解析清洗 → 结构化 → Chunk切分 → Embedding → 向量索引 + 倒排索引       │
└─────────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│                           在线检索流水线（Retrieval）                          │
│  Query → 预处理 → 多路召回 → 重排 → 上下文构造 → Prompt → LLM → 后处理 → 用户   │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 3. 详细设计文档

| 流水线 | 文档 | 说明 |
|-------|------|------|
| 离线索引 | [design-offline.md](design-offline.md) | 数据源接入、解析清洗、结构化、Chunk 切分、Embedding、索引存储 |
| 在线检索 | [design-online.md](design-online.md) | Query 预处理、多路召回、重排、上下文构造、Prompt、LLM 生成、后处理 |

### 模块详细设计

| 模块 | 文档 | 说明 |
|-----|------|------|
| Chunk 切分 | [design-chunker.md](design-chunker.md) | 切分策略、Token 计算、边界处理、配置化设计 |
| Embedding | [design-embedding.md](design-embedding.md) | Embedding 阶段流程、性能分析、异常处理、进度管理 |

### 公共参考文档

| 文档 | 说明 | 引用方 |
|-----|------|-------|
| [shared-embedding-model-spec.md](shared-embedding-model-spec.md) | Embedding 模型规格（text-embedding-v4 API、参数、约束） | design-embedding.md、design-chunker.md |
| [shared-embedding-client.md](shared-embedding-client.md) | EmbeddingClient 接口设计（批量分片、重试策略、配置） | design-embedding.md、design-online.md |

## 4. 流程图

详见 [flow.md](flow.md)

## 5. 演进路线

| 阶段 | 目标 | 关键能力 |
|-----|------|---------|
| MVP | 单数据源 + 单路向量召回 + 云端 LLM | 跑通核心链路 |
| V1 | 多数据源 + 混合召回 + 重排 | 提升召回质量 |
| V2 | Query 改写 + 意图识别 + 缓存 | 降低延迟、提升体验 |
| V3 | 本地 Embedding + 本地 LLM | 私有化、降低成本 |
| V4 | 知识图谱 + Agent 能力 | 复杂推理、多跳问答 |
