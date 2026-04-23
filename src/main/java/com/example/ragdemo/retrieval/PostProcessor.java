package com.example.ragdemo.retrieval;

import com.example.ragdemo.model.domain.RetrievalResult;

import java.util.List;

/**
 * 后处理接口 — 对 LLM 输出进行引用补充、格式化、敏感校验等
 */
public interface PostProcessor {

    /**
     * 对 LLM 生成的原始回答进行后处理
     *
     * @param rawAnswer  LLM 原始输出
     * @param references 检索引用来源
     * @return 处理后的最终回答
     */
    String process(String rawAnswer, List<RetrievalResult> references);
}
