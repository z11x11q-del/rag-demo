package com.example.ragdemo.structurer;

import com.example.ragdemo.model.domain.ParsedDocument;
import com.example.ragdemo.model.domain.Section;
import com.example.ragdemo.model.domain.StructuredDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 文档结构化处理器 — 基于 # 标题语法提取层级结构
 */
@Slf4j
@Component
public class MarkdownDocumentStructurer implements DocumentStructurer {

    private static final Set<String> SUPPORTED_TYPES = Set.of("md", "markdown");

    /** 匹配 Markdown 标题行：^#{1,6} 后跟空格和标题文本 */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");

    @Override
    public boolean supports(String fileType) {
        return fileType != null && SUPPORTED_TYPES.contains(fileType.toLowerCase());
    }

    @Override
    public StructuredDocument structure(ParsedDocument parsedDocument) {
        String rawText = parsedDocument.getRawText();
        if (rawText == null || rawText.isBlank()) {
            log.warn("Structuring skipped: rawText is empty, fileType={}", parsedDocument.getFileType());
            return new StructuredDocument(buildMetadata(parsedDocument), List.of());
        }

        List<Section> sections = parseMarkdownSections(rawText);
        List<Section> tree = buildTree(sections);
        log.info("Structuring done: fileType=md, topLevelSections={}", tree.size());
        return new StructuredDocument(buildMetadata(parsedDocument), tree);
    }

    /**
     * 逐行解析 Markdown，遇到 # 标题行时创建新 Section，其余行归入当前 Section 内容
     */
    private List<Section> parseMarkdownSections(String rawText) {
        String[] lines = rawText.split("\n");
        List<Section> flatSections = new ArrayList<>();
        Section currentSection = null;
        StringBuilder contentBuffer = new StringBuilder();

        for (String line : lines) {
            Matcher matcher = HEADING_PATTERN.matcher(line.strip());
            if (matcher.matches()) {
                // 保存上一个 section
                if (currentSection != null) {
                    currentSection.setContent(contentBuffer.toString().strip());
                    flatSections.add(currentSection);
                    contentBuffer.setLength(0);
                } else if (!contentBuffer.isEmpty()) {
                    // 第一个标题之前有正文
                    flatSections.add(new Section("", 0, contentBuffer.toString().strip()));
                    contentBuffer.setLength(0);
                }
                int level = matcher.group(1).length();
                String title = matcher.group(2).strip();
                currentSection = new Section(title, level, "");
            } else {
                if (!contentBuffer.isEmpty()) {
                    contentBuffer.append("\n");
                }
                contentBuffer.append(line);
            }
        }

        // 收尾
        if (currentSection != null) {
            currentSection.setContent(contentBuffer.toString().strip());
            flatSections.add(currentSection);
        } else if (!contentBuffer.isEmpty()) {
            flatSections.add(new Section("", 0, contentBuffer.toString().strip()));
        }

        return flatSections;
    }

    /**
     * 将扁平 Section 列表按 level 层级构建为树形结构
     * <p>
     * 使用栈思路：遍历每个 section，找到最近一个 level 比自己小的 section 作为 parent。
     * </p>
     */
    private List<Section> buildTree(List<Section> flatSections) {
        List<Section> roots = new ArrayList<>();
        // 用于记录各层级最近的 section
        Section[] stack = new Section[7]; // level 0~6

        for (Section section : flatSections) {
            int level = section.getLevel();
            if (level <= 0) {
                // 无标题 section 直接作为根
                roots.add(section);
                continue;
            }

            // 找 parent：level 比自己小的最近一个
            Section parent = null;
            for (int i = level - 1; i >= 1; i--) {
                if (stack[i] != null) {
                    parent = stack[i];
                    break;
                }
            }

            if (parent != null) {
                parent.getChildren().add(section);
            } else {
                roots.add(section);
            }

            // 更新栈：当前 level 设为自己，更深层级清空
            stack[level] = section;
            for (int i = level + 1; i < stack.length; i++) {
                stack[i] = null;
            }
        }

        return roots;
    }

    private java.util.Map<String, Object> buildMetadata(ParsedDocument parsedDocument) {
        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        if (parsedDocument.getMetadata() != null) {
            metadata.putAll(parsedDocument.getMetadata());
        }
        metadata.put("fileType", parsedDocument.getFileType());
        metadata.put("structurer", getClass().getSimpleName());
        return metadata;
    }
}
