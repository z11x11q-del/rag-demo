package com.example.ragdemo.structurer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * PDF 文档结构化处理器 — 基于段落分割和启发式标题检测
 * <p>
 * 当前阶段 PDF 解析后仅保留纯文本，无字体/样式信息，
 * 因此沿用启发式策略（短行标题检测 + 编号模式识别）。
 * 后续可扩展：当 Parser 在 metadata 中携带字体大小信息时，利用字体大小辅助标题识别。
 * </p>
 */
@Slf4j
@Component
public class PdfDocumentStructurer extends AbstractTextDocumentStructurer {

    private static final Set<String> SUPPORTED_TYPES = Set.of("pdf");

    @Override
    public boolean supports(String fileType) {
        return fileType != null && SUPPORTED_TYPES.contains(fileType.toLowerCase());
    }
}
