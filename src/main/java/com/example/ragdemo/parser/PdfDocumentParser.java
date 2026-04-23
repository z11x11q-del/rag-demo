package com.example.ragdemo.parser;

import com.example.ragdemo.model.domain.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * PDF 文档解析器 — 使用 Apache PDFBox 提取文本内容
 * <p>
 * 从 PDF 中提取纯文本和基本元数据（标题、作者、页数等）。
 * </p>
 */
@Slf4j
@Component
public class PdfDocumentParser implements DocumentParser {

    private static final Set<String> SUPPORTED_TYPES = Set.of("pdf");

    @Override
    public boolean supports(String fileType) {
        return fileType != null && SUPPORTED_TYPES.contains(fileType.toLowerCase());
    }

    @Override
    public ParsedDocument parse(InputStream input, String fileName) {
        log.info("Parsing PDF file: {}", fileName);
        try (PDDocument document = Loader.loadPDF(input.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String rawText = stripper.getText(document);

            Map<String, Object> metadata = buildMetadata(fileName, document);
            log.info("Parsed PDF file: {}, pages={}, length={}", fileName, document.getNumberOfPages(), rawText.length());
            return new ParsedDocument(rawText, "pdf", metadata);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse PDF file: " + fileName, e);
        }
    }

    private Map<String, Object> buildMetadata(String fileName, PDDocument document) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileName", fileName);
        metadata.put("parser", getClass().getSimpleName());
        metadata.put("pageCount", document.getNumberOfPages());

        PDDocumentInformation info = document.getDocumentInformation();
        if (info != null) {
            putIfNotBlank(metadata, "title", info.getTitle());
            putIfNotBlank(metadata, "author", info.getAuthor());
            putIfNotBlank(metadata, "subject", info.getSubject());
            putIfNotBlank(metadata, "creator", info.getCreator());
        }
        return metadata;
    }

    private void putIfNotBlank(Map<String, Object> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }
}
