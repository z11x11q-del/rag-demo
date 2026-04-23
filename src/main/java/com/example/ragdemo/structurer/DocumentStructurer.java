package com.example.ragdemo.structurer;

import com.example.ragdemo.model.domain.ParsedDocument;
import com.example.ragdemo.model.domain.StructuredDocument;

/**
 * 文档结构化处理接口 — 在纯文本基础上提取语义结构
 */
public interface DocumentStructurer {

    /**
     * 是否支持该文件类型
     *
     * @param fileType 文件类型（pdf, docx, md, txt 等）
     */
    boolean supports(String fileType);

    /**
     * 对解析后的文档进行结构化处理
     *
     * @param parsedDocument 解析后的文档
     * @return 结构化文档（包含章节层级）
     */
    StructuredDocument structure(ParsedDocument parsedDocument);
}
