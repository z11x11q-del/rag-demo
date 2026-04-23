package com.example.ragdemo.chunker;

/**
 * Token 计数器接口 — 用于计算文本的 token 数量
 * <p>
 * 一期使用近似估算实现，后续切换为远程精确计算。
 * </p>
 */
public interface TokenCounter {

    /**
     * 计算文本的 token 数量
     *
     * @param text 输入文本
     * @return token 数量
     */
    int countTokens(String text);
}
