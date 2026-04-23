package com.example.ragdemo.parser;

import com.example.ragdemo.model.domain.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Markdown 文档解析器 — 读取原始 Markdown 文本，保留 # 等语法标记供结构化阶段使用
 */
@Slf4j
@Component
public class MarkdownDocumentParser implements DocumentParser {

    private static final Set<String> SUPPORTED_TYPES = Set.of("md", "markdown");

    @Override
    public boolean supports(String fileType) {
        return fileType != null && SUPPORTED_TYPES.contains(fileType.toLowerCase());
    }

    @Override
    public ParsedDocument parse(InputStream input, String fileName) {
        log.info("Parsing markdown file: {}", fileName);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(line);
            }
            String rawText = sb.toString();
            log.info("Parsed markdown file: {}, length={}", fileName, rawText.length());
            return new ParsedDocument(rawText, extractFileType(fileName), buildMetadata(fileName));
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse markdown file: " + fileName, e);
        }
    }

    private String extractFileType(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1).toLowerCase() : "md";
    }

    private Map<String, Object> buildMetadata(String fileName) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileName", fileName);
        metadata.put("parser", getClass().getSimpleName());
        return metadata;
    }
}
