package com.example.ragdemo.embedding;

/**
 * Embedding 调用异常 — 封装 Embedding API 调用过程中的各类异常
 */
public class EmbeddingException extends RuntimeException {

    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
