package com.example.ragdemo.model.dto;

/**
 * RAG 问答请求
 */
public class RagQueryRequest {

    /** 用户查询文本 */
    private String query;
    /** 最大召回数量（可选，默认 5） */
    private Integer topK;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }
}
