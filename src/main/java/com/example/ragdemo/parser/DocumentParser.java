package com.example.ragdemo.parser;

import com.example.ragdemo.model.domain.ParsedDocument;

import java.io.InputStream;

/**
 * 文档解析器接口 — 将原始数据转换为纯文本
 */
public interface DocumentParser {

    /**
     * 是否支持该文件类型
     *
     * @param fileType 文件类型（pdf, docx, md, txt 等）
     */
    boolean supports(String fileType);

    /**
     * 解析原始数据为 ParsedDocument
     *
     * @param input    原始数据流
     * @param fileName 文件名
     * @return 解析后的文档
     */
    ParsedDocument parse(InputStream input, String fileName);
}
