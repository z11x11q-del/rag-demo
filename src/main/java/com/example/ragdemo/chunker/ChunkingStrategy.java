package com.example.ragdemo.chunker;

/**
 * 切分策略枚举
 */
public enum ChunkingStrategy {

    /** 固定长度切分 */
    FIXED_SIZE,
    /** 递归切分（按分隔符优先级逐级尝试） */
    RECURSIVE,
    /** 结构感知切分（利用章节层级） */
    STRUCTURE_AWARE,
    /** 语义切分（基于 Embedding 相似度） */
    SEMANTIC
}
