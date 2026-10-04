package com.example.scheduler.chat.application;

/** Provider-neutral message passed to the configured language model. */
public record ModelMessage(Role role, String content) {
    public enum Role { SYSTEM, USER, ASSISTANT }
}
