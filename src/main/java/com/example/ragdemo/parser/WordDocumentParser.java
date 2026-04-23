package com.example.ragdemo.parser;

import com.example.ragdemo.model.domain.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Word 文档解析器 — 使用 Apache POI 提取文本内容
 * <p>
 * 支持 .docx（OOXML 格式）和 .doc（OLE2 格式）两种格式。
 * </p>
 */
@Slf4j
@Component
public class WordDocumentParser implements DocumentParser {

    private static final Set<String> SUPPORTED_TYPES = Set.of("doc", "docx");

    @Override
    public boolean supports(String fileType) {
        return fileType != null && SUPPORTED_TYPES.contains(fileType.toLowerCase());
    }

    @Override
    public ParsedDocument parse(InputStream input, String fileName) {
        log.info("Parsing Word file: {}", fileName);
        String fileType = extractFileType(fileName);
        byte[] bytes;
        try {
            bytes = input.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read Word file: " + fileName, e);
        }

        String rawText;
        if ("docx".equalsIgnoreCase(fileType)) {
            rawText = parseDocx(bytes, fileName);
        } else {
            rawText = parseDoc(bytes, fileName);
        }

        Map<String, Object> metadata = buildMetadata(fileName, fileType);
        log.info("Parsed Word file: {}, length={}", fileName, rawText.length());
        return new ParsedDocument(rawText, fileType, metadata);
    }

    /**
     * 解析 .docx 格式（OOXML）
     */
    private String parseDocx(byte[] bytes, String fileName) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    if (!sb.isEmpty()) {
                        sb.append("\n\n");
                    }
                    sb.append(text.strip());
                }
            }
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse .docx file: " + fileName, e);
        }
    }

    /**
     * 解析 .doc 格式（OLE2）
     */
    private String parseDoc(byte[] bytes, String fileName) {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes));
             WordExtractor extractor = new WordExtractor(document)) {
            String[] paragraphs = extractor.getParagraphText();
            StringBuilder sb = new StringBuilder();
            for (String paragraph : paragraphs) {
                String trimmed = paragraph.strip();
                if (!trimmed.isEmpty()) {
                    if (!sb.isEmpty()) {
                        sb.append("\n\n");
                    }
                    sb.append(trimmed);
                }
            }
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse .doc file: " + fileName, e);
        }
    }

    private String extractFileType(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1).toLowerCase() : "docx";
    }

    private Map<String, Object> buildMetadata(String fileName, String fileType) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileName", fileName);
        metadata.put("parser", getClass().getSimpleName());
        metadata.put("format", fileType);
        return metadata;
    }
}
