package com.example.ragdemo.model.domain;

/**
 * 检索结果条目 — 召回阶段输出
 */
public class RetrievalResult {

    /** chunk 标识 */
    private String chunkId;
    /** 文本内容 */
    private String content;
    /** 来源文件名 */
    private String fileName;
    /** 章节路径 */
    private String titlePath;
    /** 相关性分数 */
    private double score;

    public RetrievalResult() {}

    public RetrievalResult(String chunkId, String content, double score) {
        this.chunkId = chunkId;
        this.content = content;
        this.score = score;
    }

    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getTitlePath() { return titlePath; }
    public void setTitlePath(String titlePath) { this.titlePath = titlePath; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}
