package com.example.ragdemo.controller;

import com.example.ragdemo.model.dto.TaskResponse;
import com.example.ragdemo.model.enums.TaskStatus;
import com.example.ragdemo.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务管理 API — 任务查询、重试、取消
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /**
     * 查询任务列表（支持状态过滤、分页）
     */
    @GetMapping
    public ResponseEntity<List<TaskResponse>> listTasks(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(taskService.queryTasks(status, page, size));
    }

    /**
     * 查询单个任务详情
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable String taskId) {
        return taskService.queryTask(taskId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 手动重试失败任务
     */
    @PostMapping("/{taskId}/retry")
    public ResponseEntity<Void> retry(@PathVariable String taskId) {
        taskService.retry(taskId);
        return ResponseEntity.accepted().build();
    }

    /**
     * 取消任务
     */
    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String taskId) {
        taskService.cancel(taskId);
        return ResponseEntity.ok().build();
    }
}
