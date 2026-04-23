package com.example.ragdemo.chunker;

import com.example.ragdemo.config.ChunkingProperties;
import com.example.ragdemo.embedding.EmbeddingClient;
import com.example.ragdemo.model.domain.Section;
import com.example.ragdemo.model.domain.StructuredDocument;
import com.example.ragdemo.model.domain.TextChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义切分器 — 基于 Embedding 相似度判断语义断点
 * <p>
 * 算法：先按句子分割 → 批量计算句子 Embedding → 相邻句子余弦相似度
 * → 低于阈值处标记为语义断点 → 在断点处切分 → 超长 chunk 递归细切。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticChunker {

    private static final int EMBED_BATCH_SIZE = 10;

    private final EmbeddingClient embeddingClient;
    private final TokenCounter tokenCounter;
    private final RecursiveChunker recursiveChunker;
    private final ChunkingProperties properties;

    /**
     * 对结构化文档执行语义切分
     */
    public List<TextChunk> split(StructuredDocument document) {
        List<TextChunk> allChunks = new ArrayList<>();
        for (Section section : document.getSections()) {
            allChunks.addAll(splitSection(section, ""));
        }
        reindex(allChunks);
        return allChunks;
    }

    private List<TextChunk> splitSection(Section section, String parentPath) {
        String titlePath = buildTitlePath(parentPath, section.getTitle());
        List<TextChunk> result = new ArrayList<>();

        if (section.getContent() != null && !section.getContent().isBlank()) {
            result.addAll(splitText(section.getContent(), titlePath));
        }
        for (Section child : section.getChildren()) {
            result.addAll(splitSection(child, titlePath));
        }
        return result;
    }

    /**
     * 对一段文本执行语义切分
     */
    private List<TextChunk> splitText(String text, String titlePath) {
        List<String> sentences = splitBySentence(text);
        if (sentences.size() <= 1) {
            return buildSingleChunk(text, titlePath);
        }

        List<float[]> embeddings = batchEmbed(sentences);
        List<Integer> breakpoints = findBreakpoints(embeddings);
        List<String> rawChunks = mergeByBreakpoints(sentences, breakpoints);
        return buildAndRefine(rawChunks, titlePath);
    }

    /**
     * 按句子边界分割
     */
    private List<String> splitBySentence(String text) {
        List<String> sentences = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            buffer.append(text.charAt(i));
            if (isSentenceEnd(text.charAt(i)) || i == text.length() - 1) {
                String s = buffer.toString().strip();
                if (!s.isEmpty()) {
                    sentences.add(s);
                }
                buffer.setLength(0);
            }
        }
        return sentences;
    }

    /**
     * 分批调用 Embedding（DashScope 批次上限 10 条）
     */
    private List<float[]> batchEmbed(List<String> sentences) {
        List<float[]> result = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i += EMBED_BATCH_SIZE) {
            int end = Math.min(i + EMBED_BATCH_SIZE, sentences.size());
            List<String> batch = sentences.subList(i, end);
            result.addAll(embeddingClient.embedBatch(batch));
        }
        return result;
    }

    /**
     * 计算相邻句子的余弦相似度，低于阈值处标记为断点
     */
    private List<Integer> findBreakpoints(List<float[]> embeddings) {
        double threshold = properties.getSemanticThreshold();
        List<Integer> breakpoints = new ArrayList<>();
        for (int i = 0; i < embeddings.size() - 1; i++) {
            double sim = cosineSimilarity(embeddings.get(i), embeddings.get(i + 1));
            if (sim < threshold) {
                breakpoints.add(i + 1);
            }
        }
        return breakpoints;
    }

    /**
     * 按断点位置合并句子为 chunk
     */
    private List<String> mergeByBreakpoints(List<String> sentences, List<Integer> breakpoints) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        for (int bp : breakpoints) {
            chunks.add(joinSentences(sentences, start, bp));
            start = bp;
        }
        if (start < sentences.size()) {
            chunks.add(joinSentences(sentences, start, sentences.size()));
        }
        return chunks;
    }

    /**
     * 构建 TextChunk 列表，超长 chunk 递归细切
     */
    private List<TextChunk> buildAndRefine(List<String> rawChunks, String titlePath) {
        List<TextChunk> result = new ArrayList<>();
        for (String raw : rawChunks) {
            String content = injectTitlePath(raw, titlePath);
            int tokens = tokenCounter.countTokens(content);
            if (tokens > properties.getMaxChunkSize()) {
                result.addAll(recursiveChunker.split(content, titlePath));
            } else {
                result.add(new TextChunk(0, content, titlePath, tokens));
            }
        }
        return result;
    }

    private List<TextChunk> buildSingleChunk(String text, String titlePath) {
        String content = injectTitlePath(text, titlePath);
        int tokens = tokenCounter.countTokens(content);
        if (tokens > properties.getMaxChunkSize()) {
            return recursiveChunker.split(content, titlePath);
        }
        return List.of(new TextChunk(0, content, titlePath, tokens));
    }

    private String injectTitlePath(String content, String titlePath) {
        if (properties.isInjectTitlePath() && titlePath != null && !titlePath.isBlank()) {
            return "[" + titlePath + "]\n" + content;
        }
        return content;
    }

    private String joinSentences(List<String> sentences, int from, int to) {
        return String.join(" ", sentences.subList(from, to));
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }

    private boolean isSentenceEnd(char c) {
        return c == '。' || c == '.' || c == '！' || c == '!' || c == '？' || c == '?' || c == '\n';
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
