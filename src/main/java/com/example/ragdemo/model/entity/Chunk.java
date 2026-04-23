package com.example.ragdemo.model.entity;

import com.example.ragdemo.model.enums.ChunkStatus;
import com.example.ragdemo.model.enums.IndexStatus;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文本块实体 — 对应元数据库 chunk 表
 */
public class Chunk {

    private Long id;
    /** 业务唯一标识（documentId + chunkIndex） */
    private String chunkId;
    /** 所属文档的 documentId */
    private String documentId;
    /** 在文档中的序号（从 0 开始） */
    private Integer chunkIndex;
    /** chunk 原始文本内容 */
    private String content;
    /** 所属章节标题路径（如 "安装指南 > 环境准备"） */
    private String titlePath;
    /** 扩展元数据（页码、作者、标签等） */
    private Map<String, Object> metadata;
    /** chunk 的 token 数 */
    private Integer tokenCount;
    /** 生成向量所用的模型名称及版本 */
    private String embeddingModel;
    /** 向量索引写入状态 */
    private IndexStatus denseIndexStatus;
    /** 倒排索引写入状态 */
    private IndexStatus sparseIndexStatus;
    /** chunk 状态 */
    private ChunkStatus status;
    private LocalDateTime createdAt;

    public Chunk() {}

    // ---- getters & setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTitlePath() { return titlePath; }
    public void setTitlePath(String titlePath) { this.titlePath = titlePath; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }

    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }

    public IndexStatus getDenseIndexStatus() { return denseIndexStatus; }
    public void setDenseIndexStatus(IndexStatus denseIndexStatus) { this.denseIndexStatus = denseIndexStatus; }

    public IndexStatus getSparseIndexStatus() { return sparseIndexStatus; }
    public void setSparseIndexStatus(IndexStatus sparseIndexStatus) { this.sparseIndexStatus = sparseIndexStatus; }

    public ChunkStatus getStatus() { return status; }
    public void setStatus(ChunkStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
