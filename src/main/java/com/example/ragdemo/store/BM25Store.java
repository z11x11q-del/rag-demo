package com.example.ragdemo.store;

import com.example.ragdemo.model.domain.RetrievalResult;

import java.util.List;

/**
 * 稀疏向量（倒排）索引存储接口 — BM25 关键词检索
 */
public interface BM25Store {

    /**
     * 写入文本索引
     *
     * @param id      chunk ID
     * @param content 文本内容
     */
    void index(String id, String content);

    /**
     * 批量写入文本索引
     *
     * @param ids      chunk ID 列表
     * @param contents 文本内容列表
     */
    void indexBatch(List<String> ids, List<String> contents);

    /**
     * BM25 关键词检索
     *
     * @param query 查询文本
     * @param topK  返回数量
     * @return 检索结果（按 BM25 分数降序）
     */
    List<RetrievalResult> search(String query, int topK);

    /**
     * 删除指定 ID 的索引
     *
     * @param ids chunk ID 列表
     */
    void delete(List<String> ids);
}
