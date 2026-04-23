package com.example.ragdemo.controller;

import com.example.ragdemo.model.entity.Document;
import com.example.ragdemo.model.entity.IngestionTask;
import com.example.ragdemo.model.enums.SourceType;
import com.example.ragdemo.model.enums.TaskType;
import com.example.ragdemo.pipeline.IngestionPipeline;
import com.example.ragdemo.service.DocumentLifecycleService;
import com.example.ragdemo.service.DocumentService;
import com.example.ragdemo.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * 文档管理 API — 文档接入、查询、删除
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentLifecycleService lifecycleService;
    private final TaskService taskService;
    private final IngestionPipeline ingestionPipeline;

    /**
     * 接入新文档（文件上传方式）
     * <p>
     * 流程：文件上传 → contentHash 去重 → 创建 Document 记录（status=PENDING）
     * 后续由离线流水线异步处理解析、切分、Embedding、索引写入
     * </p>
     *
     * @param file       上传的文件
     * @param sourceType 数据源类型（默认 FILE）
     * @return documentId 和处理状态
     */
    @PostMapping("/ingest")
    public ResponseEntity<?> ingest(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sourceType", defaultValue = "FILE") SourceType sourceType) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        String fileName = file.getOriginalFilename();
        String sourcePath = fileName; // MVP: 文件上传场景，sourcePath 即文件名
        byte[] content = file.getBytes();

        Optional<Document> result = documentService.ingestDocument(sourceType, sourcePath, fileName, content);

        if (result.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "message", "Document already exists with same content, skipped",
                    "documentId", documentService.generateDocumentId(sourceType, sourcePath, fileName)
            ));
        }

        Document doc = result.get();

        // 创建 IngestionTask 并提交
        IngestionTask task = new IngestionTask();
        task.setDocumentId(doc.getDocumentId());
        task.setTaskType(doc.getVersion() > 1 ? TaskType.UPDATE : TaskType.CREATE);
        String taskId = taskService.submit(task);

        // 异步触发离线索引流水线
        ingestionPipeline.run(task, new ByteArrayInputStream(content), fileName);

        return ResponseEntity.accepted().body(Map.of(
                "message", doc.getVersion() > 1 ? "Document updated, pending reprocessing" : "Document accepted, pending processing",
                "documentId", doc.getDocumentId(),
                "taskId", taskId,
                "version", doc.getVersion(),
                "status", doc.getStatus().name()
        ));
    }

    /**
     * 删除文档（软删除 + 异步清理索引）
     *
     * @param documentId 文档业务 ID
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<?> delete(@PathVariable String documentId) {
        Optional<Document> doc = documentService.findByDocumentId(documentId);
        if (doc.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        lifecycleService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 查询文档详情
     *
     * @param documentId 文档业务 ID
     */
    @GetMapping("/{documentId}")
    public ResponseEntity<?> getDocument(@PathVariable String documentId) {
        return documentService.findByDocumentId(documentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
