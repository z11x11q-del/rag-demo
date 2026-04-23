package com.example.ragdemo.model.domain;

import java.util.Map;

/**
 * 解析后的文档 — 文档解析层输出
 */
public class ParsedDocument {

    /** 原始文本内容 */
    private String rawText;
    /** 文件类型（pdf, docx, md, txt 等） */
    private String fileType;
    /** 元数据（来源、时间、作者等） */
    private Map<String, Object> metadata;

    public ParsedDocument() {}

    public ParsedDocument(String rawText, String fileType, Map<String, Object> metadata) {
        this.rawText = rawText;
        this.fileType = fileType;
        this.metadata = metadata;
    }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
