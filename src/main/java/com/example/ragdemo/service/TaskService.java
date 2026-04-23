package com.example.ragdemo.service;

import com.example.ragdemo.model.dto.TaskResponse;
import com.example.ragdemo.model.entity.IngestionTask;
import com.example.ragdemo.model.enums.TaskStage;
import com.example.ragdemo.model.enums.TaskStatus;
import com.example.ragdemo.store.MetadataStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 任务服务 — 任务提交、查询、重试、取消
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final MetadataStore metadataStore;

    /**
     * 提交新任务
     *
     * @param task 预填充了 documentId 和 taskType 的任务对象
     * @return 生成的 taskId
     */
    public String submit(IngestionTask task) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        task.setTaskId(taskId);
        task.setStatus(TaskStatus.PENDING);
        task.setStage(TaskStage.PARSING);
        task.setProgress(0);
        task.setProcessedChunks(0);
        task.setRetryCount(0);
        if (task.getMaxRetries() == null) {
            task.setMaxRetries(3);
        }
        task.setCreatedAt(LocalDateTime.now());
        metadataStore.saveTask(task);

        log.info("Task submitted: {} for document {}", taskId, task.getDocumentId());
        return taskId;
    }

    /**
     * 查询单个任务详情
     */
    public Optional<TaskResponse> queryTask(String taskId) {
        // TODO: 查询任务并转换为 TaskResponse
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * 查询任务列表（支持状态过滤、分页）
     */
    public List<TaskResponse> queryTasks(TaskStatus status, int page, int size) {
        // TODO: 实现分页查询
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * 手动重试失败任务
     */
    public void retry(String taskId) {
        // TODO: 校验任务状态、重置为 PENDING、触发重新执行
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * 取消任务
     */
    public void cancel(String taskId) {
        // TODO: 校验任务状态、标记为 CANCELLED
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
