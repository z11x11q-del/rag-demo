package com.example.ragdemo.structurer;

import com.example.ragdemo.model.domain.ParsedDocument;
import com.example.ragdemo.model.domain.Section;
import com.example.ragdemo.model.domain.StructuredDocument;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯文本启发式结构化基类 — 基于段落分割和短行标题检测提取文档结构
 * <p>
 * 适用于解析后仅保留纯文本（无字体/样式信息）的场景，如 PDF、Word、TXT。
 * </p>
 */
@Slf4j
public abstract class AbstractTextDocumentStructurer implements DocumentStructurer {

    /** 段落分隔符：连续两个换行 */
    private static final String PARAGRAPH_SEPARATOR = "\n\n";
    /** 标题最大长度（字符数），超过此长度视为正文段落 */
    private static final int TITLE_MAX_LENGTH = 80;
    /** 标题最大行数 */
    private static final int TITLE_MAX_LINES = 1;

    @Override
    public StructuredDocument structure(ParsedDocument parsedDocument) {
        String rawText = parsedDocument.getRawText();
        if (rawText == null || rawText.isBlank()) {
            log.warn("Structuring skipped: rawText is empty, fileType={}", parsedDocument.getFileType());
            return new StructuredDocument(resolveSourceType(parsedDocument), buildMetadata(parsedDocument), List.of());
        }

        List<Section> sections = splitIntoSections(rawText);
        log.info("Structuring done: fileType={}, sections={}", parsedDocument.getFileType(), sections.size());
        return new StructuredDocument(resolveSourceType(parsedDocument), buildMetadata(parsedDocument), sections);
    }

    /**
     * 将纯文本按段落分割，识别短行作为标题，构建扁平 Section 列表
     */
    protected List<Section> splitIntoSections(String rawText) {
        String[] paragraphs = rawText.split(PARAGRAPH_SEPARATOR);
        List<Section> sections = new ArrayList<>();
        Section currentSection = null;
        StringBuilder contentBuffer = new StringBuilder();

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.strip();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (looksLikeTitle(trimmed)) {
                // 将之前积累的内容保存到上一个 section
                if (currentSection != null) {
                    currentSection.setContent(contentBuffer.toString().strip());
                    sections.add(currentSection);
                    contentBuffer.setLength(0);
                } else if (!contentBuffer.isEmpty()) {
                    // 第一个标题之前有正文，创建一个无标题 section
                    sections.add(new Section("", 0, contentBuffer.toString().strip()));
                    contentBuffer.setLength(0);
                }
                currentSection = new Section(trimmed, detectLevel(trimmed), "");
            } else {
                if (!contentBuffer.isEmpty()) {
                    contentBuffer.append("\n\n");
                }
                contentBuffer.append(trimmed);
            }
        }

        // 收尾
        if (currentSection != null) {
            currentSection.setContent(contentBuffer.toString().strip());
            sections.add(currentSection);
        } else if (!contentBuffer.isEmpty()) {
            // 整篇文档没有识别到标题，作为一个完整 section
            sections.add(new Section("", 0, contentBuffer.toString().strip()));
        }

        return sections;
    }

    /**
     * 启发式判断：一段文本是否像标题
     * <ul>
     *   <li>单行且长度不超过阈值</li>
     *   <li>不以常见标点结尾（句号、逗号等）</li>
     * </ul>
     */
    protected boolean looksLikeTitle(String text) {
        if (text.length() > TITLE_MAX_LENGTH) {
            return false;
        }
        long lineCount = text.lines().count();
        if (lineCount > TITLE_MAX_LINES) {
            return false;
        }
        // 以句号、逗号、分号等结尾的大概率是正文
        return !text.matches(".*[。，；！？,.;!?]$");
    }

    /**
     * 启发式检测标题层级
     * <p>
     * 基于常见编号模式：
     * <ul>
     *   <li>"第X章" / "1." / "一、" → level 1</li>
     *   <li>"1.1" / "(一)" → level 2</li>
     *   <li>"1.1.1" → level 3</li>
     * </ul>
     * 无法识别时默认 level 1。
     * </p>
     */
    protected int detectLevel(String title) {
        String trimmed = title.strip();
        // "1.1.1" 三级编号
        if (trimmed.matches("^\\d+\\.\\d+\\.\\d+.*")) {
            return 3;
        }
        // "1.1" 二级编号
        if (trimmed.matches("^\\d+\\.\\d+.*")) {
            return 2;
        }
        // "1." 或 "第X章" 或中文编号 "一、"
        if (trimmed.matches("^\\d+\\..*") || trimmed.matches("^第.+[章节篇].*") || trimmed.matches("^[一二三四五六七八九十]+、.*")) {
            return 1;
        }
        return 1;
    }

    /**
     * 构建结构化文档的元数据，继承解析阶段元数据并追加结构化阶段信息
     */
    protected Map<String, Object> buildMetadata(ParsedDocument parsedDocument) {
        Map<String, Object> metadata = new HashMap<>();
        if (parsedDocument.getMetadata() != null) {
            metadata.putAll(parsedDocument.getMetadata());
        }
        metadata.put("fileType", parsedDocument.getFileType());
        metadata.put("structurer", getClass().getSimpleName());
        return metadata;
    }

    /**
     * 从 ParsedDocument 元数据中提取 sourceType，缺省为 FILE
     */
    protected String resolveSourceType(ParsedDocument parsedDocument) {
        if (parsedDocument.getMetadata() != null) {
            Object st = parsedDocument.getMetadata().get("sourceType");
            if (st != null) {
                return st.toString();
            }
        }
        return "FILE";
    }
}
