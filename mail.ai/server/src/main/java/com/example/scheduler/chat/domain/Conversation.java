package com.example.scheduler.chat.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Conversation(UUID id, String ownerId, String title, List<ChatMessage> messages,
                           Instant createdAt, Instant updatedAt) {
    public Conversation {
        messages = List.copyOf(messages);
    }

    public static Conversation create(String ownerId) {
        Instant now = Instant.now();
        return new Conversation(UUID.randomUUID(), ownerId, "New conversation", List.of(), now, now);
    }

    public Conversation append(ChatMessage message) {
        var updated = new java.util.ArrayList<>(messages);
        updated.add(message);
        if (updated.size() > 100) updated.subList(0, updated.size() - 100).clear();
        String newTitle = title.equals("New conversation") && message.role() == ChatMessage.Role.USER
                ? message.content().substring(0, Math.min(message.content().length(), 80)) : title;
        return new Conversation(id, ownerId, newTitle, updated, createdAt, Instant.now());
    }
}
