package com.example.ragdemo.model.entity;

import com.example.ragdemo.model.enums.TaskStage;
import com.example.ragdemo.model.enums.TaskStatus;
import com.example.ragdemo.model.enums.TaskType;

import java.time.LocalDateTime;

/**
 * 索引任务实体 — 对应元数据库 ingestion_task 表
 */
public class IngestionTask {

    private Long id;
    /** 任务唯一标识（UUID） */
    private String taskId;
    /** 关联的文档 documentId */
    private String documentId;
    /** 任务类型 */
    private TaskType taskType;
    /** 当前阶段 */
    private TaskStage stage;
    /** 任务状态 */
    private TaskStatus status;
    /** 进度百分比（0-100） */
    private Integer progress;
    /** 总 chunk 数 */
    private Integer totalChunks;
    /** 已处理 chunk 数 */
    private Integer processedChunks;
    /** 已重试次数 */
    private Integer retryCount;
    /** 最大重试次数（默认 3） */
    private Integer maxRetries;
    /** 失败时的错误信息 */
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;

    public IngestionTask() {}

    // ---- getters & setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public TaskType getTaskType() { return taskType; }
    public void setTaskType(TaskType taskType) { this.taskType = taskType; }

    public TaskStage getStage() { return stage; }
    public void setStage(TaskStage stage) { this.stage = stage; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }

    public Integer getTotalChunks() { return totalChunks; }
    public void setTotalChunks(Integer totalChunks) { this.totalChunks = totalChunks; }

    public Integer getProcessedChunks() { return processedChunks; }
    public void setProcessedChunks(Integer processedChunks) { this.processedChunks = processedChunks; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
