package com.example.ragdemo.model.dto;

import java.util.List;

/**
 * RAG 问答响应
 */
public class RagQueryResponse {

    /** LLM 生成的回答 */
    private String answer;
    /** 引用来源列表 */
    private List<ReferenceSource> references;

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public List<ReferenceSource> getReferences() { return references; }
    public void setReferences(List<ReferenceSource> references) { this.references = references; }

    /**
     * 引用来源
     */
    public static class ReferenceSource {
        private String chunkId;
        private String fileName;
        private String titlePath;
        private String content;
        private Double score;

        public String getChunkId() { return chunkId; }
        public void setChunkId(String chunkId) { this.chunkId = chunkId; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getTitlePath() { return titlePath; }
        public void setTitlePath(String titlePath) { this.titlePath = titlePath; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }
    }
}
