package com.example.ragdemo.model.entity;

import com.example.ragdemo.model.enums.DocumentStatus;
import com.example.ragdemo.model.enums.SourceType;

import java.time.LocalDateTime;

/**
 * 文档实体 — 对应元数据库 document 表
 */
public class Document {

    private Long id;
    /** 业务唯一标识（SHA256 生成） */
    private String documentId;
    /** 数据源类型 */
    private SourceType sourceType;
    /** 源路径（文件路径 / URL / 表名） */
    private String sourcePath;
    /** 文件名 */
    private String fileName;
    /** 文件内容 SHA256 哈希，用于去重和变更检测 */
    private String contentHash;
    /** 文档版本号，更新时自增 */
    private Integer version;
    /** 关联的 chunk 数量 */
    private Integer chunkCount;
    /** 文档状态 */
    private DocumentStatus status;
    /** 失败时的错误信息 */
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Document() {}

    // ---- getters & setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }

    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }

    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
