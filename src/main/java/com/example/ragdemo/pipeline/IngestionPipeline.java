package com.example.ragdemo.pipeline;

import com.example.ragdemo.chunker.Chunker;
import com.example.ragdemo.embedding.EmbeddingClient;
import com.example.ragdemo.model.domain.ParsedDocument;
import com.example.ragdemo.model.domain.StructuredDocument;
import com.example.ragdemo.model.domain.TextChunk;
import com.example.ragdemo.model.entity.Chunk;
import com.example.ragdemo.model.entity.Document;
import com.example.ragdemo.model.entity.IngestionTask;
import com.example.ragdemo.model.enums.ChunkStatus;
import com.example.ragdemo.model.enums.DocumentStatus;
import com.example.ragdemo.model.enums.IndexStatus;
import com.example.ragdemo.model.enums.TaskStage;
import com.example.ragdemo.model.enums.TaskStatus;
import com.example.ragdemo.parser.DocumentParser;
import com.example.ragdemo.store.BM25Store;
import com.example.ragdemo.store.MetadataStore;
import com.example.ragdemo.store.VectorStore;
import com.example.ragdemo.structurer.DocumentStructurer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 离线索引流水线 — 编排完整的 Ingestion 流程
 * <p>
 * 数据源 → 解析清洗 → 结构化 → Chunk 切分 → Embedding → 向量索引 + 倒排索引
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionPipeline {

    private final List<DocumentParser> parsers;
    private final List<DocumentStructurer> structurers;
    private final Chunker chunker;
    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;
    private final BM25Store bm25Store;
    private final MetadataStore metadataStore;

    /**
     * 异步执行完整的离线索引流水线
     *
     * @param task        索引任务
     * @param inputStream 原始数据流
     * @param fileName    文件名
     */
    @Async("ingestionTaskExecutor")
    public void run(IngestionTask task, InputStream inputStream, String fileName) {
        log.info("Pipeline started for task: {}, document: {}, file: {}", task.getTaskId(), task.getDocumentId(), fileName);
        try {
            markRunning(task);
            ParsedDocument parsed = doParse(task, inputStream, fileName);
            StructuredDocument structured = doStructure(task, parsed);
            List<Chunk> chunks = doChunk(task, structured);
            List<float[]> vectors = doEmbed(task, chunks);
            boolean indexOk = doIndex(task, chunks, vectors);
            finishPipeline(task, chunks, indexOk);
        } catch (Exception e) {
            handleFailure(task, e);
        }
    }

    // ==================== 流水线各阶段 ====================

    /**
     * 标记任务为运行中，文档为处理中
     */
    private void markRunning(IngestionTask task) {
        task.setStatus(TaskStatus.RUNNING);
        task.setStartedAt(LocalDateTime.now());
        metadataStore.updateTask(task);
        metadataStore.updateDocumentStatus(task.getDocumentId(), DocumentStatus.PROCESSING);
    }

    /**
     * 阶段 1 — PARSING: 选择合适的 parser 解析文件为纯文本
     */
    private ParsedDocument doParse(IngestionTask task, InputStream inputStream, String fileName) {
        updateStage(task, TaskStage.PARSING, 5);
        DocumentParser parser = selectParser(fileName);
        ParsedDocument parsed = parser.parse(inputStream, fileName);
        log.info("Task {}: PARSING done, rawText length={}", task.getTaskId(), parsed.getRawText().length());
        updateStage(task, TaskStage.PARSING, 15);
        return parsed;
    }

    /**
     * 阶段 2 — STRUCTURING: 结构化处理，提取章节层级
     */
    private StructuredDocument doStructure(IngestionTask task, ParsedDocument parsed) {
        updateStage(task, TaskStage.STRUCTURING, 20);
        DocumentStructurer structurer = selectStructurer(parsed.getFileType());
        StructuredDocument structured = structurer.structure(parsed);
        log.info("Task {}: STRUCTURING done, sections={}", task.getTaskId(), structured.getSections().size());
        updateStage(task, TaskStage.STRUCTURING, 30);
        return structured;
    }

    /**
     * 阶段 3 — CHUNKING: 切分为文本块，持久化到元数据库
     */
    private List<Chunk> doChunk(IngestionTask task, StructuredDocument structured) {
        updateStage(task, TaskStage.CHUNKING, 35);
        String documentId = task.getDocumentId();
        List<TextChunk> textChunks = chunker.split(structured);
        log.info("Task {}: CHUNKING done, chunks={}", task.getTaskId(), textChunks.size());

        List<Chunk> chunks = textChunks.stream().map(tc -> {
            Chunk chunk = new Chunk();
            chunk.setChunkId(documentId + "#" + tc.getIndex());
            chunk.setDocumentId(documentId);
            chunk.setChunkIndex(tc.getIndex());
            chunk.setContent(tc.getContent());
            chunk.setTitlePath(tc.getTitlePath());
            chunk.setTokenCount(tc.getTokenCount());
            chunk.setStatus(ChunkStatus.INDEXING);
            chunk.setDenseIndexStatus(IndexStatus.PENDING);
            chunk.setSparseIndexStatus(IndexStatus.PENDING);
            chunk.setCreatedAt(LocalDateTime.now());
            return chunk;
        }).toList();

        metadataStore.saveChunks(chunks);
        task.setTotalChunks(chunks.size());
        updateStage(task, TaskStage.CHUNKING, 45);
        return chunks;
    }

    /**
     * 阶段 4 — EMBEDDING: 批量生成稠密向量
     */
    private List<float[]> doEmbed(IngestionTask task, List<Chunk> chunks) {
        updateStage(task, TaskStage.EMBEDDING, 50);
        List<String> texts = chunks.stream().map(Chunk::getContent).toList();
        List<float[]> vectors = embeddingClient.embedBatch(texts);
        String modelName = embeddingClient.modelName();
        chunks.forEach(c -> c.setEmbeddingModel(modelName));
        log.info("Task {}: EMBEDDING done, vectors={}, model={}", task.getTaskId(), vectors.size(), modelName);
        updateStage(task, TaskStage.EMBEDDING, 65);
        return vectors;
    }

    /**
     * 阶段 5 — INDEXING: 顺序写入 Dense + Sparse 索引（带补偿），更新 chunk 最终状态
     *
     * @return true 表示全部索引成功，false 表示部分失败
     */
    private boolean doIndex(IngestionTask task, List<Chunk> chunks, List<float[]> vectors) {
        updateStage(task, TaskStage.INDEXING, 70);
        List<String> chunkIds = chunks.stream().map(Chunk::getChunkId).toList();

        boolean denseOk = writeDenseIndex(task, chunks, chunkIds, vectors);
        updateStage(task, TaskStage.INDEXING, 80);

        boolean sparseOk = writeSparseIndex(task, chunks, chunkIds);
        updateStage(task, TaskStage.INDEXING, 90);

        resolveChunkStatus(chunks);
        metadataStore.saveChunks(chunks);
        return denseOk && sparseOk;
    }

    /**
     * 写入 Dense 向量索引
     */
    private boolean writeDenseIndex(IngestionTask task, List<Chunk> chunks, List<String> chunkIds, List<float[]> vectors) {
        try {
            List<Map<String, Object>> metadatas = chunks.stream().map(c -> {
                Map<String, Object> meta = new HashMap<>();
                meta.put("documentId", c.getDocumentId());
                meta.put("chunkIndex", c.getChunkIndex());
                meta.put("titlePath", c.getTitlePath());
                return meta;
            }).toList();
            vectorStore.upsertBatch(chunkIds, vectors, metadatas);
            chunks.forEach(c -> c.setDenseIndexStatus(IndexStatus.SUCCESS));
            log.info("Task {}: Dense index written, count={}", task.getTaskId(), chunkIds.size());
            return true;
        } catch (Exception e) {
            log.error("Task {}: Dense index write failed", task.getTaskId(), e);
            chunks.forEach(c -> c.setDenseIndexStatus(IndexStatus.FAILED));
            return false;
        }
    }

    /**
     * 写入 Sparse 倒排索引
     */
    private boolean writeSparseIndex(IngestionTask task, List<Chunk> chunks, List<String> chunkIds) {
        try {
            List<String> contents = chunks.stream().map(Chunk::getContent).toList();
            bm25Store.indexBatch(chunkIds, contents);
            chunks.forEach(c -> c.setSparseIndexStatus(IndexStatus.SUCCESS));
            log.info("Task {}: Sparse index written, count={}", task.getTaskId(), chunkIds.size());
            return true;
        } catch (Exception e) {
            log.error("Task {}: Sparse index write failed", task.getTaskId(), e);
            chunks.forEach(c -> c.setSparseIndexStatus(IndexStatus.FAILED));
            return false;
        }
    }

    /**
     * 根据 Dense/Sparse 写入结果决定每个 chunk 最终状态
     */
    private void resolveChunkStatus(List<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            boolean dense = chunk.getDenseIndexStatus() == IndexStatus.SUCCESS;
            boolean sparse = chunk.getSparseIndexStatus() == IndexStatus.SUCCESS;
            if (dense && sparse) {
                chunk.setStatus(ChunkStatus.ACTIVE);
            } else if (!dense && !sparse) {
                chunk.setStatus(ChunkStatus.INDEX_FAILED);
            } else {
                chunk.setStatus(ChunkStatus.INDEX_PARTIAL);
            }
        }
    }

    /**
     * 阶段 6 — 收尾: 根据索引结果更新任务和文档最终状态
     */
    private void finishPipeline(IngestionTask task, List<Chunk> chunks, boolean indexOk) {
        task.setProcessedChunks(chunks.size());
        task.setFinishedAt(LocalDateTime.now());

        if (indexOk) {
            task.setStage(TaskStage.DONE);
            task.setStatus(TaskStatus.SUCCESS);
            task.setProgress(100);
            metadataStore.updateTask(task);

            metadataStore.findDocumentById(task.getDocumentId()).ifPresent(doc -> {
                doc.setStatus(DocumentStatus.ACTIVE);
                doc.setChunkCount(chunks.size());
                doc.setUpdatedAt(LocalDateTime.now());
                metadataStore.saveDocument(doc);
            });
            log.info("Task {}: Pipeline completed successfully, chunks={}", task.getTaskId(), chunks.size());
        } else {
            task.setStage(TaskStage.INDEXING);
            task.setStatus(TaskStatus.FAILED);
            task.setProgress(90);
            task.setErrorMessage("Index write partially failed");
            metadataStore.updateTask(task);

            metadataStore.updateDocumentStatus(task.getDocumentId(), DocumentStatus.FAILED);
            log.warn("Task {}: Pipeline finished with partial index failure", task.getTaskId());
        }
    }

    /**
     * 异常兜底 — 标记任务和文档为失败
     */
    private void handleFailure(IngestionTask task, Exception e) {
        log.error("Task {}: Pipeline failed at stage {}", task.getTaskId(), task.getStage(), e);
        task.setStatus(TaskStatus.FAILED);
        task.setErrorMessage(e.getMessage());
        task.setFinishedAt(LocalDateTime.now());
        metadataStore.updateTask(task);
        metadataStore.updateDocumentStatus(task.getDocumentId(), DocumentStatus.FAILED);
    }

    // ==================== 工具方法 ====================

    /**
     * 更新任务阶段和进度
     */
    private void updateStage(IngestionTask task, TaskStage stage, int progress) {
        task.setStage(stage);
        task.setProgress(progress);
        metadataStore.updateTask(task);
    }

    /**
     * 根据文件类型选择合适的解析器
     */
    private DocumentParser selectParser(String fileName) {
        String fileType = extractFileType(fileName);
        return parsers.stream()
                .filter(p -> p.supports(fileType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No parser found for file type: " + fileType));
    }

    /**
     * 根据文件类型选择合适的结构化处理器
     */
    private DocumentStructurer selectStructurer(String fileType) {
        return structurers.stream()
                .filter(s -> s.supports(fileType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No structurer found for file type: " + fileType));
    }

    /**
     * 从文件名中提取文件类型
     */
    private String extractFileType(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1).toLowerCase() : "";
    }
}
