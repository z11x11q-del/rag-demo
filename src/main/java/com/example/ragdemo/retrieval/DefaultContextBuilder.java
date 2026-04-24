package com.example.ragdemo.retrieval;

import com.example.ragdemo.chunker.TokenCounter;
import com.example.ragdemo.model.domain.RetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 上下文构造默认实现 — 去重 → 排序 → Token 截断 → 编号格式化
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class DefaultContextBuilder implements ContextBuilder {

    private static final String EMPTY_CONTEXT = "（未检索到相关参考文档）";
    private static final int DEFAULT_TOKEN_BUDGET = 3000;

    private final TokenCounter tokenCounter;

    @Override
    public String build(List<RetrievalResult> results) {
        if (results == null || results.isEmpty()) {
            return EMPTY_CONTEXT;
        }
        List<RetrievalResult> deduped = deduplicate(results);
        deduped.sort(Comparator.comparingDouble(RetrievalResult::getScore).reversed());
        List<RetrievalResult> truncated = truncateByTokenBudget(deduped, DEFAULT_TOKEN_BUDGET);
        return format(truncated);
    }

    private List<RetrievalResult> deduplicate(List<RetrievalResult> results) {
        Map<String, RetrievalResult> seen = new LinkedHashMap<>();
        for (RetrievalResult r : results) {
            seen.merge(r.getChunkId(), r,
                (existing, incoming) ->
                    incoming.getScore() > existing.getScore() ? incoming : existing);
        }
        return new ArrayList<>(seen.values());
    }

    private List<RetrievalResult> truncateByTokenBudget(List<RetrievalResult> results, int tokenBudget) {
        List<RetrievalResult> truncated = new ArrayList<>();
        int usedTokens = 0;
        for (RetrievalResult r : results) {
            if (r.getContent() == null) {
                log.warn("跳过 content 为 null 的结果: chunkId={}", r.getChunkId());
                continue;
            }
            int cost = tokenCounter.countTokens(r.getContent()) + tokenCounter.countTokens(formatMeta(r));
            if (usedTokens + cost > tokenBudget) {
                break;
            }
            truncated.add(r);
            usedTokens += cost;
        }
        return truncated;
    }

    private String formatMeta(RetrievalResult r) {
        if (r.getFileName() == null && r.getTitlePath() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder("来源：");
        if (r.getFileName() != null) sb.append(r.getFileName());
        if (r.getTitlePath() != null) sb.append(" | 章节：").append(r.getTitlePath());
        return sb.toString();
    }

    private String format(List<RetrievalResult> results) {
        if (results.isEmpty()) {
            return EMPTY_CONTEXT;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            appendChunk(sb, results.get(i), i + 1);
        }
        return sb.toString().strip();
    }

    private void appendChunk(StringBuilder sb, RetrievalResult r, int index) {
        sb.append("[").append(index).append("] ");
        String meta = formatMeta(r);
        if (!meta.isEmpty()) {
            sb.append(meta).append("\n");
        }
        sb.append(r.getContent()).append("\n\n");
    }
}
