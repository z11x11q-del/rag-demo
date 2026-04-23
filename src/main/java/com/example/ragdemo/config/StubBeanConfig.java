package com.example.ragdemo.config;

import com.example.ragdemo.embedding.EmbeddingClient;
import com.example.ragdemo.llm.LlmClient;
import com.example.ragdemo.model.dto.RagQueryResponse;
import com.example.ragdemo.retrieval.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Collections;
import java.util.List;

/**
 * Stub Bean 配置 — 为尚未实现的接口提供占位实现，确保 Spring 容器能正常启动。
 * <p>
 * 每个 Bean 使用 @ConditionalOnMissingBean，一旦有真实实现注册，stub 会自动退让。
 * </p>
 * <p>
 * 仅在 dev / local 等开发环境生效，生产环境应禁用此配置。
 * </p>
 */
@Slf4j
@Configuration
@Profile({"dev", "local"})
public class StubBeanConfig {

    private static final String STUB_WARN = "[STUB] {} is not implemented yet";

    // ========== 离线流水线 ==========

    @Bean
    @ConditionalOnMissingBean
    public EmbeddingClient stubEmbeddingClient() {
        return new EmbeddingClient() {
            @Override
            public float[] embed(String text) {
                log.warn(STUB_WARN, "EmbeddingClient");
                throw new UnsupportedOperationException("EmbeddingClient not implemented");
            }

            @Override
            public List<float[]> embedBatch(List<String> texts) {
                log.warn(STUB_WARN, "EmbeddingClient");
                throw new UnsupportedOperationException("EmbeddingClient not implemented");
            }

            @Override
            public int dimension() { return 0; }

            @Override
            public String modelName() { return "stub"; }
        };
    }

    // ========== 在线流水线 ==========

    @Bean
    @ConditionalOnMissingBean
    public QueryPreprocessor stubQueryPreprocessor() {
        return rawQuery -> {
            log.warn(STUB_WARN, "QueryPreprocessor");
            return new QueryPreprocessor.ProcessedQuery(rawQuery, "qa");
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public Reranker stubReranker() {
        return (query, candidates, topN) -> {
            log.warn(STUB_WARN, "Reranker");
            return candidates.stream().limit(topN).toList();
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public ContextBuilder stubContextBuilder() {
        return results -> {
            log.warn(STUB_WARN, "ContextBuilder");
            return "";
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public PromptBuilder stubPromptBuilder() {
        return (context, query, intent) -> {
            log.warn(STUB_WARN, "PromptBuilder");
            return query;
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public PostProcessor stubPostProcessor() {
        return (rawAnswer, references) -> {
            log.warn(STUB_WARN, "PostProcessor");
            var response = new RagQueryResponse();
            response.setAnswer(rawAnswer);
            response.setReferences(Collections.emptyList());
            return response;
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public LlmClient stubLlmClient() {
        return new LlmClient() {
            @Override
            public String chat(String prompt) {
                log.warn(STUB_WARN, "LlmClient");
                throw new UnsupportedOperationException("LlmClient not implemented");
            }

            @Override
            public Iterable<String> chatStream(String prompt) {
                log.warn(STUB_WARN, "LlmClient");
                throw new UnsupportedOperationException("LlmClient not implemented");
            }

            @Override
            public String modelName() { return "stub"; }
        };
    }
}
