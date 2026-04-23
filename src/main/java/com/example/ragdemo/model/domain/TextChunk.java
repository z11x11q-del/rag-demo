package com.example.ragdemo.model.domain;

/**
 * 文本块 — Chunk 切分层输出（尚未持久化）
 */
public class TextChunk {

    /** 在文档中的序号 */
    private int index;
    /** 文本内容 */
    private String content;
    /** 所属章节标题路径 */
    private String titlePath;
    /** token 数量 */
    private int tokenCount;

    public TextChunk() {}

    public TextChunk(int index, String content, String titlePath, int tokenCount) {
        this.index = index;
        this.content = content;
        this.titlePath = titlePath;
        this.tokenCount = tokenCount;
    }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTitlePath() { return titlePath; }
    public void setTitlePath(String titlePath) { this.titlePath = titlePath; }

    public int getTokenCount() { return tokenCount; }
    public void setTokenCount(int tokenCount) { this.tokenCount = tokenCount; }
}
