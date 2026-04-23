package com.example.ragdemo.store.memory;

import com.example.ragdemo.model.entity.Chunk;
import com.example.ragdemo.model.entity.Document;
import com.example.ragdemo.model.entity.IngestionTask;
import com.example.ragdemo.model.enums.ChunkStatus;
import com.example.ragdemo.model.enums.DocumentStatus;
import com.example.ragdemo.model.enums.TaskStatus;
import com.example.ragdemo.store.MetadataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 内存实现的元数据存储 — MVP 阶段使用，后续替换为 PostgreSQL
 */
@Component
public class InMemoryMetadataStore implements MetadataStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryMetadataStore.class);

    private final Map<String, Document> documents = new ConcurrentHashMap<>();
    private final Map<String, Chunk> chunks = new ConcurrentHashMap<>();
    private final Map<String, IngestionTask> tasks = new ConcurrentHashMap<>();
    private final AtomicLong docIdSeq = new AtomicLong(1);
    private final AtomicLong chunkIdSeq = new AtomicLong(1);
    private final AtomicLong taskIdSeq = new AtomicLong(1);

    // ========== Document 操作 ==========

    @Override
    public void saveDocument(Document document) {
        if (document.getId() == null) {
            document.setId(docIdSeq.getAndIncrement());
        }
        documents.put(document.getDocumentId(), document);
        log.debug("Saved document: {}", document.getDocumentId());
    }

    @Override
    public Optional<Document> findDocumentById(String documentId) {
        return Optional.ofNullable(documents.get(documentId));
    }

    @Override
    public Optional<Document> findDocumentByContentHash(String contentHash) {
        return documents.values().stream()
                .filter(d -> contentHash.equals(d.getContentHash()))
                .findFirst();
    }

    @Override
    public List<Document> findDocumentsByStatus(DocumentStatus status) {
        return documents.values().stream()
                .filter(d -> d.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public void updateDocumentStatus(String documentId, DocumentStatus status) {
        Document doc = documents.get(documentId);
        if (doc != null) {
            doc.setStatus(status);
            log.debug("Updated document {} status to {}", documentId, status);
        }
    }

    @Override
    public void deleteDocument(String documentId) {
        documents.remove(documentId);
        log.debug("Deleted document: {}", documentId);
    }

    // ========== Chunk 操作 ==========

    @Override
    public void saveChunks(List<Chunk> chunkList) {
        for (Chunk chunk : chunkList) {
            if (chunk.getId() == null) {
                chunk.setId(chunkIdSeq.getAndIncrement());
            }
            chunks.put(chunk.getChunkId(), chunk);
        }
        log.debug("Saved {} chunks", chunkList.size());
    }

    @Override
    public List<Chunk> findChunksByDocumentId(String documentId) {
        return chunks.values().stream()
                .filter(c -> documentId.equals(c.getDocumentId()))
                .sorted(Comparator.comparingInt(Chunk::getChunkIndex))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Chunk> findChunkById(String chunkId) {
        return Optional.ofNullable(chunks.get(chunkId));
    }

    @Override
    public List<Chunk> findChunksByStatus(ChunkStatus status) {
        return chunks.values().stream()
                .filter(c -> c.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public void updateChunkStatus(String chunkId, ChunkStatus status) {
        Chunk chunk = chunks.get(chunkId);
        if (chunk != null) {
            chunk.setStatus(status);
        }
    }

    @Override
    public void deleteChunksByDocumentId(String documentId) {
        List<String> toRemove = chunks.values().stream()
                .filter(c -> documentId.equals(c.getDocumentId()))
                .map(Chunk::getChunkId)
                .collect(Collectors.toList());
        toRemove.forEach(chunks::remove);
        log.debug("Deleted {} chunks for document {}", toRemove.size(), documentId);
    }

    // ========== Task 操作 ==========

    @Override
    public void saveTask(IngestionTask task) {
        if (task.getId() == null) {
            task.setId(taskIdSeq.getAndIncrement());
        }
        tasks.put(task.getTaskId(), task);
        log.debug("Saved task: {}", task.getTaskId());
    }

    @Override
    public Optional<IngestionTask> findTaskById(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    @Override
    public List<IngestionTask> findTasksByStatus(TaskStatus status) {
        return tasks.values().stream()
                .filter(t -> t.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<IngestionTask> findAllTasks(int page, int size) {
        return tasks.values().stream()
                .sorted(Comparator.comparing(IngestionTask::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());
    }

    @Override
    public void updateTask(IngestionTask task) {
        tasks.put(task.getTaskId(), task);
        log.debug("Updated task: {}", task.getTaskId());
    }
}
