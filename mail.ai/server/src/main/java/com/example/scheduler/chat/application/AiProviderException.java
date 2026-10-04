package com.example.scheduler.chat.application;

/** Provider-neutral failure details consumed by the chat use case. */
public class AiProviderException extends RuntimeException {
    private final String code;
    private final boolean retryable;
    private final String userMessage;

    public AiProviderException(String code, boolean retryable, String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.code = code;
        this.retryable = retryable;
        this.userMessage = userMessage;
    }

    public String code() { return code; }
    public boolean retryable() { return retryable; }
    public String userMessage() { return userMessage; }
}
