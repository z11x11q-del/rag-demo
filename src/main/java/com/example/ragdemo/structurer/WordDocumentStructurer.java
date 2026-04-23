package com.example.ragdemo.structurer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Word 文档结构化处理器 — 基于段落分割和启发式标题检测
 * <p>
 * 当前阶段 Word 解析后仅保留纯文本，无样式信息，
 * 因此沿用启发式策略（短行标题检测 + 编号模式识别）。
 * 后续可扩展：当 Parser 在 metadata 中携带 Word 大纲级别信息时，可精准还原标题层级。
 * </p>
 */
@Slf4j
@Component
public class WordDocumentStructurer extends AbstractTextDocumentStructurer {

    private static final Set<String> SUPPORTED_TYPES = Set.of("doc", "docx");

    @Override
    public boolean supports(String fileType) {
        return fileType != null && SUPPORTED_TYPES.contains(fileType.toLowerCase());
    }
}
