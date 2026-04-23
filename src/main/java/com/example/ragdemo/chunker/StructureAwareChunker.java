package com.example.ragdemo.chunker;

import com.example.ragdemo.config.ChunkingProperties;
import com.example.ragdemo.model.domain.Section;
import com.example.ragdemo.model.domain.StructuredDocument;
import com.example.ragdemo.model.domain.TextChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构感知切分器 — 利用 Section 层级信息优先在章节边界切分
 * <p>
 * 深度优先遍历 Section 树：
 * - 叶子 Section 内容 ≤ chunkSize → 整体作为一个 chunk
 * - 叶子 Section 内容 &gt; chunkSize → 递归切分
 * - titlePath 注入到每个 chunk 头部，提升检索语义完整性
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructureAwareChunker {

    private final TokenCounter tokenCounter;
    private final RecursiveChunker recursiveChunker;
    private final ChunkingProperties properties;

    /**
     * 对结构化文档执行结构感知切分
     */
    public List<TextChunk> split(StructuredDocument document) {
        List<TextChunk> chunks = new ArrayList<>();
        for (Section section : document.getSections()) {
            splitSection(section, "", chunks);
        }
        reindex(chunks);
        return chunks;
    }

    /**
     * 递归处理单个 Section 及其子 Section
     */
    private void splitSection(Section section, String parentPath, List<TextChunk> chunks) {
        String titlePath = buildTitlePath(parentPath, section.getTitle());

        if (section.getChildren().isEmpty()) {
            splitLeafSection(section, titlePath, chunks);
        } else {
            splitBranchSection(section, titlePath, chunks);
        }
    }

    /**
     * 叶子 Section：内容小直接作为 chunk，否则递归切分
     */
    private void splitLeafSection(Section section, String titlePath, List<TextChunk> chunks) {
        String content = buildContent(section, titlePath);
        if (content.isBlank()) {
            return;
        }

        int tokens = tokenCounter.countTokens(content);
        if (tokens <= properties.getChunkSize()) {
            chunks.add(new TextChunk(0, content, titlePath, tokens));
        } else {
            chunks.addAll(recursiveChunker.split(content, titlePath));
        }
    }

    /**
     * 非叶子 Section：子 Section 总量小则合并，否则各自处理
     */
    private void splitBranchSection(Section section, String titlePath, List<TextChunk> chunks) {
        int totalTokens = estimateSectionTokens(section);

        if (totalTokens <= properties.getChunkSize()) {
            String merged = collectAllContent(section, titlePath);
            if (!merged.isBlank()) {
                chunks.add(new TextChunk(0, merged, titlePath, tokenCounter.countTokens(merged)));
            }
        } else {
            addOwnContent(section, titlePath, chunks);
            for (Section child : section.getChildren()) {
                splitSection(child, titlePath, chunks);
            }
        }
    }

    /**
     * 将 Section 自身内容（非子 Section 内容）作为 chunk
     */
    private void addOwnContent(Section section, String titlePath, List<TextChunk> chunks) {
        String ownContent = section.getContent();
        if (ownContent == null || ownContent.isBlank()) {
            return;
        }
        String content = buildContent(ownContent, titlePath);
        int tokens = tokenCounter.countTokens(content);
        if (tokens > 0) {
            chunks.add(new TextChunk(0, content, titlePath, tokens));
        }
    }

    /**
     * 递归收集 Section 及所有子 Section 的内容
     */
    private String collectAllContent(Section section, String titlePath) {
        StringBuilder sb = new StringBuilder();
        if (properties.isInjectTitlePath() && !titlePath.isBlank()) {
            sb.append("[").append(titlePath).append("]\n");
        }
        appendContent(sb, section);
        return sb.toString().strip();
    }

    private void appendContent(StringBuilder sb, Section section) {
        if (section.getContent() != null && !section.getContent().isBlank()) {
            sb.append(section.getContent()).append("\n");
        }
        for (Section child : section.getChildren()) {
            if (child.getTitle() != null && !child.getTitle().isBlank()) {
                sb.append(child.getTitle()).append("\n");
            }
            appendContent(sb, child);
        }
    }

    private int estimateSectionTokens(Section section) {
        int tokens = tokenCounter.countTokens(section.getContent() != null ? section.getContent() : "");
        for (Section child : section.getChildren()) {
            tokens += estimateSectionTokens(child);
        }
        return tokens;
    }

    private String buildContent(Section section, String titlePath) {
        String raw = section.getContent() != null ? section.getContent() : "";
        return buildContent(raw, titlePath);
    }

    private String buildContent(String rawContent, String titlePath) {
        if (properties.isInjectTitlePath() && titlePath != null && !titlePath.isBlank()) {
            return "[" + titlePath + "]\n" + rawContent;
        }
        return rawContent;
    }

    private String buildTitlePath(String parentPath, String title) {
        if (title == null || title.isBlank()) {
            return parentPath;
        }
        if (parentPath == null || parentPath.isBlank()) {
            return title;
        }
        return parentPath + properties.getTitlePathSeparator() + title;
    }

    private void reindex(List<TextChunk> chunks) {
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setIndex(i);
        }
    }
}
