package com.example.ragdemo.store;

import com.example.ragdemo.model.entity.Chunk;
import com.example.ragdemo.model.entity.Document;
import com.example.ragdemo.model.entity.IngestionTask;
import com.example.ragdemo.model.enums.ChunkStatus;
import com.example.ragdemo.model.enums.DocumentStatus;
import com.example.ragdemo.model.enums.TaskStatus;

import java.util.List;
import java.util.Optional;

/**
 * 元数据存储接口 — 事实源（Source of Truth），管理文档、Chunk、任务的 CRUD
 */
public interface MetadataStore {

    // ========== Document 操作 ==========

    void saveDocument(Document document);

    Optional<Document> findDocumentById(String documentId);

    Optional<Document> findDocumentByContentHash(String contentHash);

    List<Document> findDocumentsByStatus(DocumentStatus status);

    void updateDocumentStatus(String documentId, DocumentStatus status);

    void deleteDocument(String documentId);

    // ========== Chunk 操作 ==========

    void saveChunks(List<Chunk> chunks);

    List<Chunk> findChunksByDocumentId(String documentId);

    Optional<Chunk> findChunkById(String chunkId);

    List<Chunk> findChunksByStatus(ChunkStatus status);

    void updateChunkStatus(String chunkId, ChunkStatus status);

    void deleteChunksByDocumentId(String documentId);

    // ========== Task 操作 ==========

    void saveTask(IngestionTask task);

    Optional<IngestionTask> findTaskById(String taskId);

    List<IngestionTask> findTasksByStatus(TaskStatus status);

    List<IngestionTask> findAllTasks(int page, int size);

    void updateTask(IngestionTask task);
}
