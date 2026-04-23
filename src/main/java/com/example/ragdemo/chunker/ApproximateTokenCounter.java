package com.example.ragdemo.chunker;

import org.springframework.stereotype.Component;

/**
 * 近似 Token 计数器 — 基于中英文字符比例估算 token 数量
 * <p>
 * 中文：约 1.5 字符 / token（基于 BPE 分词特性）<br/>
 * 英文：约 4 字符 / token<br/>
 * 误差范围：±15%，对切分过程可接受。
 * </p>
 * <p>
 * TODO: 后续对接远程 tokenizer 服务或利用 DashScope usage.prompt_tokens 校准
 * </p>
 */
@Component
public class ApproximateTokenCounter implements TokenCounter {

    private static final double CJK_CHARS_PER_TOKEN = 1.5;
    private static final double OTHER_CHARS_PER_TOKEN = 4.0;

    @Override
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = countCjkChars(text);
        int other = text.length() - cjk;
        return (int) Math.ceil(cjk / CJK_CHARS_PER_TOKEN + other / OTHER_CHARS_PER_TOKEN);
    }

    private int countCjkChars(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isCjk(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    private boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }
}
