package com.example.ragdemo.retrieval;

import com.example.ragdemo.config.QueryProperties;
import com.example.ragdemo.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Query 预处理默认实现 — 清洗、规范化、LLM 改写、LLM 意图识别
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class DefaultQueryPreprocessor implements QueryPreprocessor {

    private static final String DEFAULT_INTENT = "qa";
    private static final Set<String> VALID_INTENTS = Set.of("qa", "summary", "comparison", "how_to");

    private static final String REWRITE_SYSTEM_PROMPT =
            "你是一个查询改写助手。请将用户的口语化问题改写为更适合文档检索的查询。只输出改写后的查询，不要解释。";

    private static final String INTENT_SYSTEM_PROMPT =
            "请判断以下查询的意图类型，只返回意图标签。可选标签：qa, summary, comparison, how_to";

    private final LlmClient llmClient;
    private final QueryProperties queryProperties;

    @Override
    public ProcessedQuery process(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            throw new IllegalArgumentException("rawQuery 不能为空");
        }

        // Step 1 + 2: 清洗 + 规范化
        String cleaned = normalize(clean(rawQuery));

        // Step 3: LLM 改写（失败时降级使用清洗后的 query）
        String rewritten = rewrite(cleaned);

        // Step 4: LLM 意图识别（失败时降级为默认意图 qa）
        String intent = recognizeIntent(rewritten);

        log.debug("Query预处理: raw='{}' → rewritten='{}', intent='{}'", rawQuery, rewritten, intent);
        return new ProcessedQuery(rewritten, intent);
    }

    // ---- Step 1: 清洗 ----

    private String clean(String raw) {
        int maxLen = queryProperties.getMaxQueryLength();
        String result = raw.strip();
        result = result.replaceAll("[\\p{Cntrl}]", "");  // 移除控制字符
        result = result.replaceAll("\\s+", " ");          // 连续空白归一
        if (result.length() > maxLen) {
            log.warn("rawQuery 超长（{}字符），截断至{}字符", result.length(), maxLen);
            result = result.substring(0, maxLen);
        }
        return result;
    }

    // ---- Step 2: 规范化 ----

    private String normalize(String text) {
        // 全角字母/数字转半角
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c >= '\uFF01' && c <= '\uFF5E') {
                sb.append((char) (c - '\uFEE0'));
            } else {
                sb.append(c);
            }
        }
        // 统一中文问号为英文问号
        return sb.toString().replace('？', '?');
    }

    // ---- Step 3: LLM 改写 ----

    private String rewrite(String cleaned) {
        if (!queryProperties.isRewriteEnabled()) {
            return cleaned;
        }
        try {
            String result = llmClient.chat(REWRITE_SYSTEM_PROMPT, cleaned);
            String rewritten = (result != null && !result.isBlank()) ? result.strip() : cleaned;
            log.debug("LLM 改写: '{}' → '{}'", cleaned, rewritten);
            return rewritten;
        } catch (Exception e) {
            log.warn("LLM 改写失败，降级使用清洗后的 query: {}", e.getMessage());
            return cleaned;
        }
    }

    // ---- Step 4: LLM 意图识别 ----

    private String recognizeIntent(String query) {
        if (!queryProperties.isIntentRecognitionEnabled()) {
            return DEFAULT_INTENT;
        }
        try {
            String result = llmClient.chat(INTENT_SYSTEM_PROMPT, query);
            String intent = (result != null) ? result.strip().toLowerCase() : DEFAULT_INTENT;
            intent = VALID_INTENTS.contains(intent) ? intent : DEFAULT_INTENT;
            log.debug("LLM 意图识别: query='{}' → intent='{}'", query, intent);
            return intent;
        } catch (Exception e) {
            log.warn("LLM 意图识别失败，降级使用默认意图: {}", e.getMessage());
            return DEFAULT_INTENT;
        }
    }
}
