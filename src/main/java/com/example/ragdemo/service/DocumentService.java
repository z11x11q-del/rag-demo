package com.example.ragdemo.service;

import com.example.ragdemo.model.entity.Document;
import com.example.ragdemo.model.enums.DocumentStatus;
import com.example.ragdemo.model.enums.SourceType;
import com.example.ragdemo.store.MetadataStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * 文档服务 — 去重判断、文档元数据管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final MetadataStore metadataStore;

    /**
     * 检查文档是否重复（基于 contentHash）
     *
     * @param contentHash 文件内容哈希
     * @return 已存在的文档（如果有）
     */
    public Optional<Document> checkDuplicate(String contentHash) {
        return metadataStore.findDocumentByContentHash(contentHash);
    }

    /**
     * 生成文档唯一标识（基于 数据源类型 + 源路径 + 文件名 的 SHA256）
     */
    public String generateDocumentId(SourceType sourceType, String sourcePath, String fileName) {
        String raw = sourceType.name() + ":" + sourcePath + ":" + fileName;
        return sha256(raw);
    }

    /**
     * 计算内容的 SHA256 哈希
     */
    public String computeContentHash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * 接入新文档 — 去重检查 + 创建文档记录
     *
     * @return 新创建的 Document（status=PENDING），或者已存在且内容未变更时返回 empty
     */
    public Optional<Document> ingestDocument(SourceType sourceType, String sourcePath,
                                              String fileName, byte[] content) {
        String contentHash = computeContentHash(content);
        String documentId = generateDocumentId(sourceType, sourcePath, fileName);

        // 去重检查
        Optional<Document> existing = metadataStore.findDocumentById(documentId);
        if (existing.isPresent()) {
            Document doc = existing.get();
            if (contentHash.equals(doc.getContentHash())) {
                log.info("Document {} already exists with same content, skipping", documentId);
                return Optional.empty(); // 内容未变更，跳过
            }
            // 内容变更 → 更新版本号
            log.info("Document {} content changed, updating version from {}", documentId, doc.getVersion());
            doc.setContentHash(contentHash);
            doc.setVersion(doc.getVersion() + 1);
            doc.setStatus(DocumentStatus.PENDING);
            doc.setUpdatedAt(LocalDateTime.now());
            metadataStore.saveDocument(doc);
            return Optional.of(doc);
        }

        // 新文档
        Document doc = new Document();
        doc.setDocumentId(documentId);
        doc.setSourceType(sourceType);
        doc.setSourcePath(sourcePath);
        doc.setFileName(fileName);
        doc.setContentHash(contentHash);
        doc.setVersion(1);
        doc.setChunkCount(0);
        doc.setStatus(DocumentStatus.PENDING);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        metadataStore.saveDocument(doc);

        log.info("Created new document: {} ({})", documentId, fileName);
        return Optional.of(doc);
    }

    /**
     * 保存文档记录到元数据库
     */
    public void saveDocument(Document document) {
        metadataStore.saveDocument(document);
    }

    /**
     * 根据 documentId 查询文档
     */
    public Optional<Document> findByDocumentId(String documentId) {
        return metadataStore.findDocumentById(documentId);
    }

    /**
     * 查询所有指定状态的文档
     */
    public List<Document> findByStatus(DocumentStatus status) {
        return metadataStore.findDocumentsByStatus(status);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
