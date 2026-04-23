package com.example.ragdemo.model.domain;

import java.util.List;
import java.util.Map;

/**
 * 结构化文档 — 结构化处理层输出
 */
public class StructuredDocument {

    /** 数据源类型（FILE / URL / DB / API / LOG） */
    private String sourceType;
    /** 元数据 */
    private Map<String, Object> metadata;
    /** 章节列表 */
    private List<Section> sections;

    public StructuredDocument() {}

    public StructuredDocument(String sourceType, Map<String, Object> metadata, List<Section> sections) {
        this.sourceType = sourceType;
        this.metadata = metadata;
        this.sections = sections;
    }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public List<Section> getSections() { return sections; }
    public void setSections(List<Section> sections) { this.sections = sections; }
}
