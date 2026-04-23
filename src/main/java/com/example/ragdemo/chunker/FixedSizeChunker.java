package com.example.ragdemo.chunker;

import com.example.ragdemo.config.ChunkingProperties;
import com.example.ragdemo.model.domain.TextChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定长度切分器 — 按目标 token 数滑动窗口切分
 * <p>
 * 适用于 FAQ / 短文本等无明确结构的场景。
 * 切分时尽量对齐到句子边界，相邻 chunk 保留 overlap。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FixedSizeChunker {

    private final TokenCounter tokenCounter;
    private final ChunkingProperties properties;

    /**
     * 固定长度切分文本
     */
    public List<TextChunk> split(String text, String titlePath) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> sentences = splitBySentence(text);
        List<String> rawChunks = mergeSentences(sentences);
        return buildTextChunks(rawChunks, titlePath);
    }

    /**
     * 按句子边界分割文本
     */
    private List<String> splitBySentence(String text) {
        List<String> sentences = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            buffer.append(c);
            if (isSentenceEnd(c) || i == text.length() - 1) {
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
     * 合并句子直到接近 chunkSize，保留 overlap
     */
    private List<String> mergeSentences(List<String> sentences) {
        List<String> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int overlapTokens = properties.getChunkOverlap();

        for (String sentence : sentences) {
            String candidate = buffer.isEmpty() ? sentence : buffer + " " + sentence;
            if (tokenCounter.countTokens(candidate) > properties.getChunkSize() && !buffer.isEmpty()) {
                chunks.add(buffer.toString());
                buffer = new StringBuilder(tailOverlap(buffer.toString(), overlapTokens));
                buffer.append(" ").append(sentence);
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

    private String tailOverlap(String text, int overlapTokens) {
        if (overlapTokens <= 0) {
            return "";
        }
        int charEstimate = (int) (overlapTokens * 3.0);
        int start = Math.max(0, text.length() - charEstimate);
        return text.substring(start);
    }

    private boolean isSentenceEnd(char c) {
        return c == '。' || c == '.' || c == '！' || c == '!' || c == '？' || c == '?' || c == '\n';
    }

    private List<TextChunk> buildTextChunks(List<String> rawChunks, String titlePath) {
        List<TextChunk> result = new ArrayList<>();
        for (int i = 0; i < rawChunks.size(); i++) {
            String content = rawChunks.get(i).strip();
            if (content.isEmpty()) {
                continue;
            }
            result.add(new TextChunk(i, content, titlePath, tokenCounter.countTokens(content)));
        }
        return result;
    }
}
