package com.example.ragdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Chunk 切分参数配置 — 支持通过 application.yml 外部化配置
 */
@Configuration
@ConfigurationProperties(prefix = "rag.chunking")
public class ChunkingProperties {

    /** 单个 chunk 的目标长度（tokens） */
    private int chunkSize = 512;
    /** 相邻 chunk 重叠区域长度（tokens） */
    private int chunkOverlap = 64;
    /** 过短的 chunk 合并阈值（tokens） */
    private int minChunkSize = 100;
    /** 超长 chunk 强制再切分阈值（tokens） */
    private int maxChunkSize = 1024;

    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }

    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }

    public int getMinChunkSize() { return minChunkSize; }
    public void setMinChunkSize(int minChunkSize) { this.minChunkSize = minChunkSize; }

    public int getMaxChunkSize() { return maxChunkSize; }
    public void setMaxChunkSize(int maxChunkSize) { this.maxChunkSize = maxChunkSize; }
}
