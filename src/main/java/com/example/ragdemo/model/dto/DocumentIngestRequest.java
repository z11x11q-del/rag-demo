package com.example.ragdemo.model.dto;

import com.example.ragdemo.model.enums.SourceType;

/**
 * 文档上传/接入请求
 */
public class DocumentIngestRequest {

    /** 数据源类型 */
    private SourceType sourceType;
    /** 源路径（文件路径 / URL / 表名） */
    private String sourcePath;
    /** 文件名 */
    private String fileName;

    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }

    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
}
