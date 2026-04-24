package com.example.ragdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 多路召回配置参数 — 对应 application.yml 中的 {@code rag.retrieval.*}
 */
@Configuration
@ConfigurationProperties(prefix = "rag.retrieval")
public class RetrievalProperties {

    /** 是否启用稠密向量召回通道 */
    private boolean denseEnabled = true;
    /** 是否启用 BM25 稀疏召回通道 */
    private boolean sparseEnabled = true;
    /** RRF 平滑常数（经验值 60，无需调参） */
    private int rrfK = 60;
    /** 默认每通道 TopK */
    private int defaultTopK = 10;
    /** 融合后最低分数阈值，低于此值的结果丢弃（0.0 = 不过滤） */
    private double scoreThreshold = 0.0;

    public boolean isDenseEnabled() { return denseEnabled; }
    public void setDenseEnabled(boolean denseEnabled) { this.denseEnabled = denseEnabled; }

    public boolean isSparseEnabled() { return sparseEnabled; }
    public void setSparseEnabled(boolean sparseEnabled) { this.sparseEnabled = sparseEnabled; }

    public int getRrfK() { return rrfK; }
    public void setRrfK(int rrfK) { this.rrfK = rrfK; }

    public int getDefaultTopK() { return defaultTopK; }
    public void setDefaultTopK(int defaultTopK) { this.defaultTopK = defaultTopK; }

    public double getScoreThreshold() { return scoreThreshold; }
    public void setScoreThreshold(double scoreThreshold) { this.scoreThreshold = scoreThreshold; }
}
