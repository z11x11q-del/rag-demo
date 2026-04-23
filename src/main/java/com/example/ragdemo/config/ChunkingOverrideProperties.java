package com.example.ragdemo.config;

/**
 * 按数据源类型覆盖的 Chunking 参数
 */
public class ChunkingOverrideProperties {

    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer minChunkSize;
    private Integer maxChunkSize;
    private String defaultStrategy;

    public Integer getChunkSize() { return chunkSize; }
    public void setChunkSize(Integer chunkSize) { this.chunkSize = chunkSize; }

    public Integer getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(Integer chunkOverlap) { this.chunkOverlap = chunkOverlap; }

    public Integer getMinChunkSize() { return minChunkSize; }
    public void setMinChunkSize(Integer minChunkSize) { this.minChunkSize = minChunkSize; }

    public Integer getMaxChunkSize() { return maxChunkSize; }
    public void setMaxChunkSize(Integer maxChunkSize) { this.maxChunkSize = maxChunkSize; }

    public String getDefaultStrategy() { return defaultStrategy; }
    public void setDefaultStrategy(String defaultStrategy) { this.defaultStrategy = defaultStrategy; }
}
