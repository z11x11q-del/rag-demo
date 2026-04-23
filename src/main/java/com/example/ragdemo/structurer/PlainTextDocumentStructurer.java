package com.example.ragdemo.structurer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 纯文本文档结构化处理器 — 基于段落分割和启发式标题检测
 */
@Slf4j
@Component
public class PlainTextDocumentStructurer extends AbstractTextDocumentStructurer {

    private static final Set<String> SUPPORTED_TYPES = Set.of("txt", "text");

    @Override
    public boolean supports(String fileType) {
        return fileType != null && SUPPORTED_TYPES.contains(fileType.toLowerCase());
    }
}
