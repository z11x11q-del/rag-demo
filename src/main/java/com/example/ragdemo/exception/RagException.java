package com.example.ragdemo.exception;

/**
 * RAG 业务异常基类 — 所有业务自定义异常的根异常
 */
public class RagException extends RuntimeException {

    public RagException(String message) {
        super(message);
    }

    public RagException(String message, Throwable cause) {
        super(message, cause);
    }
}
