package com.example.ragdemo.service;

import com.example.ragdemo.model.entity.Chunk;
import com.example.ragdemo.model.entity.Document;
import com.example.ragdemo.model.enums.ChunkStatus;
import com.example.ragdemo.model.enums.DocumentStatus;
import com.example.ragdemo.store.BM25Store;
import com.example.ragdemo.store.MetadataStore;
import com.example.ragdemo.store.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 文档生命周期服务 — 处理文档的更新和删除（先写新再删旧策略）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentLifecycleService {

    private final MetadataStore metadataStore;
    private final VectorStore vectorStore;
    private final BM25Store bm25Store;

    /**
     * 更新文档 — 先写新版本再删旧版本，避免检索空窗期
     *
     * @param documentId 文档 ID
     */
    public void updateDocument(String documentId) {
        // TODO: 实现先写新再删旧策略（需配合 IngestionPipeline 使用）
        // 1. 按新增流程处理新版本
        // 2. 新版本索引写入成功后，删除旧版本的 chunks 和向量
        // 3. 更新元数据库中的 document 记录（版本号 +1）
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * 删除文档 — 软删除 + 级联清理索引
     * <p>
     * 步骤：
     * 1. 标记 document 状态为 DELETED
     * 2. 查询所有关联 chunks
     * 3. 删除向量索引中对应的 vectorIds
     * 4. 删除倒排索引中对应的 chunkIds
     * 5. 标记 chunks 为 DELETED
     * 6. 清理元数据库中的 chunk 记录
     * 7. 清理元数据库中的 document 记录
     * </p>
     *
     * @param documentId 文档 ID
     */
    public void deleteDocument(String documentId) {
        Optional<Document> docOpt = metadataStore.findDocumentById(documentId);
        if (docOpt.isEmpty()) {
            log.warn("Document {} not found, skip deletion", documentId);
            return;
        }

        Document doc = docOpt.get();
        log.info("Deleting document: {} ({})", documentId, doc.getFileName());

        // 1. 先标记文档为 DELETED（软删除），让在线检索立即过滤掉
        metadataStore.updateDocumentStatus(documentId, DocumentStatus.DELETED);

        // 2. 查询所有关联 chunks
        List<Chunk> chunks = metadataStore.findChunksByDocumentId(documentId);
        if (!chunks.isEmpty()) {
            List<String> chunkIds = chunks.stream().map(Chunk::getChunkId).toList();

            // 3. 删除向量索引
            try {
                vectorStore.delete(chunkIds);
                log.debug("Deleted {} vectors for document {}", chunkIds.size(), documentId);
            } catch (Exception e) {
                log.error("Failed to delete vectors for document {}, will retry later", documentId, e);
                // 补偿：后续定时任务会扫描 DELETED 状态的文档，重新尝试清理
            }

            // 4. 删除倒排索引
            try {
                bm25Store.delete(chunkIds);
                log.debug("Deleted {} BM25 entries for document {}", chunkIds.size(), documentId);
            } catch (Exception e) {
                log.error("Failed to delete BM25 entries for document {}, will retry later", documentId, e);
            }

            // 5. 清理 chunk 元数据
            metadataStore.deleteChunksByDocumentId(documentId);
        }

        // 6. 清理 document 元数据
        metadataStore.deleteDocument(documentId);
        log.info("Document {} deleted successfully", documentId);
    }
}
