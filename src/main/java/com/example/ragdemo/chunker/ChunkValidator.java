package com.example.ragdemo.chunker;

import com.example.ragdemo.config.ChunkingProperties;
import com.example.ragdemo.model.domain.TextChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Chunk 质量校验器 — 对切分结果执行合规性检查并修复异常 chunk
 * <p>
 * 校验规则：长度合规、非空、非纯标点
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkValidator {

    private static final Pattern PUNCTUATION_ONLY = Pattern.compile("^[\\p{Punct}\\p{Space}\\p{IsPunctuation}]+$");

    private final TokenCounter tokenCounter;
    private final ChunkingProperties properties;

    /**
     * 校验并修复 chunk 列表：合并过短、丢弃无效
     */
    public List<TextChunk> validate(List<TextChunk> chunks) {
        List<TextChunk> result = new ArrayList<>();
        for (TextChunk chunk : chunks) {
            if (!isValid(chunk)) {
                handleInvalid(chunk, result);
            } else {
                result.add(chunk);
            }
        }
        reindex(result);
        return result;
    }

    private boolean isValid(TextChunk chunk) {
        String content = chunk.getContent();
        if (content == null || content.isBlank()) {
            return false;
        }
        if (PUNCTUATION_ONLY.matcher(content).matches()) {
            return false;
        }
        return chunk.getTokenCount() >= properties.getMinChunkSize();
    }

    /**
     * 过短 chunk 合并到前一个；空/无效 chunk 丢弃
     */
    private void handleInvalid(TextChunk chunk, List<TextChunk> result) {
        String content = chunk.getContent();
        if (content == null || content.isBlank() || PUNCTUATION_ONLY.matcher(content).matches()) {
            log.debug("Discarded empty/punctuation-only chunk, index={}", chunk.getIndex());
            return;
        }
        if (!result.isEmpty()) {
            mergeToLast(result, chunk);
            log.debug("Merged short chunk to previous, index={}", chunk.getIndex());
        } else {
            result.add(chunk);
        }
    }

    private void mergeToLast(List<TextChunk> result, TextChunk chunk) {
        TextChunk last = result.get(result.size() - 1);
        String merged = last.getContent() + "\n" + chunk.getContent();
        last.setContent(merged);
        last.setTokenCount(tokenCounter.countTokens(merged));
    }

    private void reindex(List<TextChunk> chunks) {
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setIndex(i);
        }
    }
}
