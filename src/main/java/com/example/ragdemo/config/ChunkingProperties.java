package com.example.ragdemo.config;

import com.example.ragdemo.chunker.ChunkingStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /** 默认切分策略 */
    private ChunkingStrategy defaultStrategy = ChunkingStrategy.STRUCTURE_AWARE;
    /** 递归切分的分隔符优先级 */
    private List<String> separatorPriority = Arrays.asList("\n\n", "\n", "。", ". ", "，", ", ", " ");
    /** 是否将章节标题注入 chunk 头部 */
    private boolean injectTitlePath = true;
    /** 标题路径分隔符 */
    private String titlePathSeparator = " > ";
    /** 语义切分的相似度断点阈值（低于此值视为语义断点） */
    private double semanticThreshold = 0.5;
    /** 按数据源类型覆盖参数 */
    private Map<String, ChunkingOverrideProperties> overrides = new HashMap<>();

    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }

    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }

    public int getMinChunkSize() { return minChunkSize; }
    public void setMinChunkSize(int minChunkSize) { this.minChunkSize = minChunkSize; }

    public int getMaxChunkSize() { return maxChunkSize; }
    public void setMaxChunkSize(int maxChunkSize) { this.maxChunkSize = maxChunkSize; }

    public ChunkingStrategy getDefaultStrategy() { return defaultStrategy; }
    public void setDefaultStrategy(ChunkingStrategy defaultStrategy) { this.defaultStrategy = defaultStrategy; }

    public List<String> getSeparatorPriority() { return separatorPriority; }
    public void setSeparatorPriority(List<String> separatorPriority) { this.separatorPriority = separatorPriority; }

    public boolean isInjectTitlePath() { return injectTitlePath; }
    public void setInjectTitlePath(boolean injectTitlePath) { this.injectTitlePath = injectTitlePath; }

    public String getTitlePathSeparator() { return titlePathSeparator; }
    public void setTitlePathSeparator(String titlePathSeparator) { this.titlePathSeparator = titlePathSeparator; }

    public double getSemanticThreshold() { return semanticThreshold; }
    public void setSemanticThreshold(double semanticThreshold) { this.semanticThreshold = semanticThreshold; }

    public Map<String, ChunkingOverrideProperties> getOverrides() { return overrides; }
    public void setOverrides(Map<String, ChunkingOverrideProperties> overrides) { this.overrides = overrides; }
}
