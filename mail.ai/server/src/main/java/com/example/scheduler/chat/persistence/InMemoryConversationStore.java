package com.example.scheduler.chat.persistence;

import com.example.scheduler.chat.domain.Conversation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "app.chat.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryConversationStore implements ConversationStore {
    private final ConcurrentHashMap<UUID, Conversation> conversations = new ConcurrentHashMap<>();

    @Override
    public Conversation save(Conversation conversation) {
        conversations.put(conversation.id(), conversation);
        return conversation;
    }

    @Override
    public Optional<Conversation> findByIdAndOwnerId(UUID id, String ownerId) {
        return Optional.ofNullable(conversations.get(id)).filter(c -> c.ownerId().equals(ownerId));
    }

    @Override
    public List<Conversation> findRecentByOwnerId(String ownerId, int limit) {
        return conversations.values().stream().filter(c -> c.ownerId().equals(ownerId))
                .sorted(Comparator.comparing(Conversation::updatedAt).reversed()).limit(limit).toList();
    }
}
