package com.example.ragdemo.service;

import com.example.ragdemo.llm.LlmClient;
import com.example.ragdemo.model.domain.RetrievalResult;
import com.example.ragdemo.model.dto.RagQueryRequest;
import com.example.ragdemo.model.dto.RagQueryResponse;
import com.example.ragdemo.retrieval.PostProcessor;
import com.example.ragdemo.retrieval.PromptBuilder;
import com.example.ragdemo.retrieval.QueryPreprocessor;
import com.example.ragdemo.retrieval.QueryPreprocessor.ProcessedQuery;
import com.example.ragdemo.retrieval.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * RAG 问答门面服务 — 编排完整的在线检索流水线
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class RagService {

    private static final int DEFAULT_TOP_K = 10;
    private static final int DEFAULT_TOP_N = 5;

    private final QueryPreprocessor queryPreprocessor;
    private final RetrievalService retrievalService;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final PostProcessor postProcessor;

    /**
     * 执行完整的 RAG 问答流程
     *
     * @param request 用户查询请求
     * @return 问答响应（包含回答和引用来源）
     */
    public RagQueryResponse answer(RagQueryRequest request) {
        int topK = Optional.ofNullable(request.getTopK()).orElse(DEFAULT_TOP_K);
        int topN = Math.max(1, topK / 2);

        // 1. Query 预处理（清洗 / 改写 / 意图识别）
        ProcessedQuery processed = queryPreprocessor.process(request.getQuery());

        // 2. 多路召回 + 重排
        List<RetrievalResult> results = retrievalService.retrieve(processed.rewrittenQuery(), topK, topN);

        // 3. 上下文构造
        String context = retrievalService.buildContext(results);

        // 4. Prompt 组装
        PromptBuilder.Prompt prompt = promptBuilder.build(context, processed.rewrittenQuery(), processed.intent());

        // 5. LLM 生成
        String rawAnswer = llmClient.chat(prompt.systemPrompt(), prompt.userMessage());

        // 6. 后处理（引用补充 / 格式化 / 校验）
        return postProcessor.process(rawAnswer, results);
    }
}
