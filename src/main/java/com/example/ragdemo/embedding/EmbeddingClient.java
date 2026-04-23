package com.example.ragdemo.embedding;

import java.util.List;

/**
 * Embedding 客户端接口 — 将文本转化为稠密向量，屏蔽本地/远程差异
 */
public interface EmbeddingClient {

    /**
     * 单条文本 Embedding
     *
     * @param text 输入文本
     * @return 稠密向量
     */
    float[] embed(String text);

    /**
     * 批量文本 Embedding
     *
     * @param texts 文本列表
     * @return 向量列表（与输入一一对应）
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 返回向量维度
     */
    int dimension();

    /**
     * 返回当前使用的模型名称
     */
    String modelName();
}
