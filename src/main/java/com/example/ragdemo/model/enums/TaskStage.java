package com.example.ragdemo.model.enums;

/**
 * 任务阶段枚举 — 离线流水线各阶段
 */
public enum TaskStage {
    PARSING,
    STRUCTURING,
    CHUNKING,
    EMBEDDING,
    INDEXING,
    DONE
}
