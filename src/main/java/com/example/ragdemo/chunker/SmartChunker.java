package com.example.ragdemo.chunker;

import com.example.ragdemo.config.ChunkingOverrideProperties;
import com.example.ragdemo.config.ChunkingProperties;
import com.example.ragdemo.model.domain.StructuredDocument;
import com.example.ragdemo.model.domain.TextChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 智能切分路由器 — Chunker 接口的主实现
 * <p>
 * 根据 StructuredDocument.sourceType 和配置的 overrides 自动路由到合适的切分策略。
 * 路由优先级：overrides 指定策略 → defaultStrategy → STRUCTURE_AWARE 兜底。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmartChunker implements Chunker {

    private final FixedSizeChunker fixedSizeChunker;
    private final RecursiveChunker recursiveChunker;
    private final StructureAwareChunker structureAwareChunker;
    private final SemanticChunker semanticChunker;
    private final ChunkValidator chunkValidator;
    private final ChunkingProperties properties;

    @Override
    public List<TextChunk> split(StructuredDocument document) {
        ChunkingStrategy strategy = resolveStrategy(document.getSourceType());
        log.info("Chunking with strategy={}, sourceType={}", strategy, document.getSourceType());

        List<TextChunk> chunks = doSplit(document, strategy);
        List<TextChunk> validated = chunkValidator.validate(chunks);

        log.info("Chunking done: raw={}, validated={}", chunks.size(), validated.size());
        return validated;
    }

    /**
     * 根据 sourceType 解析切分策略：先查 overrides，再用 defaultStrategy
     */
    private ChunkingStrategy resolveStrategy(String sourceType) {
        if (sourceType != null) {
            ChunkingOverrideProperties override = properties.getOverrides().get(sourceType);
            if (override != null && override.getDefaultStrategy() != null) {
                return parseStrategy(override.getDefaultStrategy());
            }
        }
        return properties.getDefaultStrategy();
    }

    /**
     * 按策略分发到具体切分器
     */
    private List<TextChunk> doSplit(StructuredDocument document, ChunkingStrategy strategy) {
        return switch (strategy) {
            case FIXED_SIZE -> splitFixed(document);
            case RECURSIVE -> splitRecursive(document);
            case STRUCTURE_AWARE -> structureAwareChunker.split(document);
            case SEMANTIC -> semanticChunker.split(document);
        };
    }

    /**
     * 固定长度：将所有 Section 内容拼接后切分
     */
    private List<TextChunk> splitFixed(StructuredDocument document) {
        String allText = collectAllText(document);
        return fixedSizeChunker.split(allText, "");
    }

    /**
     * 递归切分：将所有 Section 内容拼接后切分
     */
    private List<TextChunk> splitRecursive(StructuredDocument document) {
        String allText = collectAllText(document);
        return recursiveChunker.split(allText, "");
    }

    private String collectAllText(StructuredDocument document) {
        StringBuilder sb = new StringBuilder();
        if (document.getSections() != null) {
            for (var section : document.getSections()) {
                appendSection(sb, section);
            }
        }
        return sb.toString().strip();
    }

    private void appendSection(StringBuilder sb, com.example.ragdemo.model.domain.Section section) {
        if (section.getTitle() != null && !section.getTitle().isBlank()) {
            sb.append(section.getTitle()).append("\n");
        }
        if (section.getContent() != null && !section.getContent().isBlank()) {
            sb.append(section.getContent()).append("\n\n");
        }
        for (var child : section.getChildren()) {
            appendSection(sb, child);
        }
    }

    private ChunkingStrategy parseStrategy(String name) {
        try {
            return ChunkingStrategy.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown strategy '{}', falling back to default", name);
            return properties.getDefaultStrategy();
        }
    }
}
