package com.example.ragdemo.chunker;

import com.example.ragdemo.config.ChunkingProperties;
import com.example.ragdemo.model.domain.TextChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归切分器 — 按分隔符优先级逐级尝试切分，通用兜底策略
 * <p>
 * 分隔符优先级（从粗到细）：段落 → 行 → 句子 → 子句 → 词。
 * 算法：用最高优先级分隔符分割 → 合并到 chunkSize → 超长递归细切 → overlap 保留。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecursiveChunker {

    private final TokenCounter tokenCounter;
    private final ChunkingProperties properties;

    /**
     * 递归切分文本为 TextChunk 列表
     */
    public List<TextChunk> split(String text, String titlePath) {
        List<String> separators = properties.getSeparatorPriority();
        List<String> rawChunks = recursiveSplit(text, separators, 0);
        return buildTextChunks(rawChunks, titlePath);
    }

    private List<String> recursiveSplit(String text, List<String> separators, int depth) {
        int tokens = tokenCounter.countTokens(text);
        if (tokens <= properties.getChunkSize()) {
            return List.of(text);
        }
        if (depth >= separators.size()) {
            return List.of(text);
        }

        String sep = separators.get(depth);
        String[] segments = text.split(escapeRegex(sep), -1);
        if (segments.length <= 1) {
            return recursiveSplit(text, separators, depth + 1);
        }

        List<String> chunks = mergeSegments(segments, sep);
        return refineOversized(chunks, separators, depth);
    }

    /**
     * 合并相邻片段直到接近 chunkSize，保留 overlap
     */
    private List<String> mergeSegments(String[] segments, String sep) {
        List<String> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (String segment : segments) {
            String candidate = buffer.isEmpty() ? segment : buffer + sep + segment;
            if (tokenCounter.countTokens(candidate) > properties.getChunkSize() && !buffer.isEmpty()) {
                chunks.add(buffer.toString());
                buffer = new StringBuilder(buildOverlap(buffer.toString()));
                buffer.append(segment);
            } else {
                buffer.setLength(0);
                buffer.append(candidate);
            }
        }
        if (!buffer.isEmpty()) {
            chunks.add(buffer.toString());
        }
        return chunks;
    }

    /**
     * 对超长 chunk 递归细切
     */
    private List<String> refineOversized(List<String> chunks, List<String> separators, int depth) {
        List<String> result = new ArrayList<>();
        for (String chunk : chunks) {
            if (tokenCounter.countTokens(chunk) > properties.getMaxChunkSize() && depth + 1 < separators.size()) {
                result.addAll(recursiveSplit(chunk, separators, depth + 1));
            } else {
                result.add(chunk);
            }
        }
        return result;
    }

    /**
     * 从文本尾部截取约 overlapTokens 大小的内容作为 overlap
     */
    private String buildOverlap(String text) {
        int overlapTokens = properties.getChunkOverlap();
        if (overlapTokens <= 0 || text.isEmpty()) {
            return "";
        }
        return tailByTokens(text, overlapTokens);
    }

    /**
     * 从文本尾部按 token 数截取，尽量对齐到句子边界
     */
    private String tailByTokens(String text, int targetTokens) {
        int charEstimate = (int) (targetTokens * 3.0);
        int start = Math.max(0, text.length() - charEstimate);
        String tail = text.substring(start);

        while (tokenCounter.countTokens(tail) > targetTokens && start < text.length()) {
            start += 10;
            tail = text.substring(Math.min(start, text.length()));
        }
        return alignToSentenceBoundary(text, start);
    }

    /**
     * 尝试将截取位置对齐到最近的句子结尾
     */
    private String alignToSentenceBoundary(String text, int approxStart) {
        int searchStart = Math.max(0, approxStart - 20);
        int searchEnd = Math.min(text.length(), approxStart + 20);
        String window = text.substring(searchStart, searchEnd);

        int bestPos = findLastSentenceEnd(window);
        if (bestPos >= 0) {
            return text.substring(searchStart + bestPos + 1);
        }
        return text.substring(approxStart);
    }

    private int findLastSentenceEnd(String text) {
        int best = -1;
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '.' || c == '！' || c == '?' || c == '\n') {
                best = i;
                break;
            }
        }
        return best;
    }

    private List<TextChunk> buildTextChunks(List<String> rawChunks, String titlePath) {
        List<TextChunk> result = new ArrayList<>();
        for (int i = 0; i < rawChunks.size(); i++) {
            String content = rawChunks.get(i).strip();
            if (content.isEmpty()) {
                continue;
            }
            int tokens = tokenCounter.countTokens(content);
            result.add(new TextChunk(i, content, titlePath, tokens));
        }
        return result;
    }

    private String escapeRegex(String literal) {
        return java.util.regex.Pattern.quote(literal);
    }
}
