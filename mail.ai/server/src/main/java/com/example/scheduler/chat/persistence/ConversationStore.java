package com.example.scheduler.chat.persistence;

import com.example.scheduler.chat.domain.Conversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationStore {
    Conversation save(Conversation conversation);
    Optional<Conversation> findByIdAndOwnerId(UUID id, String ownerId);
    List<Conversation> findRecentByOwnerId(String ownerId, int limit);
}
