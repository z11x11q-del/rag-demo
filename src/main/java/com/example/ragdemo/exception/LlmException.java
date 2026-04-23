package com.example.ragdemo.exception;

/**
 * LLM 调用异常 — 覆盖 API 调用失败、响应解析错误、重试耗尽等场景
 */
public class LlmException extends RagException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
