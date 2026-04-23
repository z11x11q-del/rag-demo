package com.example.ragdemo.model.dto;

import com.example.ragdemo.model.enums.TaskStatus;

import java.time.LocalDateTime;

/**
 * 任务查询响应
 */
public class TaskResponse {

    private String taskId;
    private String documentId;
    private String taskType;
    private String stage;
    private TaskStatus status;
    private Integer progress;
    private Integer totalChunks;
    private Integer processedChunks;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    // ---- getters & setters ----

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }

    public Integer getTotalChunks() { return totalChunks; }
    public void setTotalChunks(Integer totalChunks) { this.totalChunks = totalChunks; }

    public Integer getProcessedChunks() { return processedChunks; }
    public void setProcessedChunks(Integer processedChunks) { this.processedChunks = processedChunks; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
