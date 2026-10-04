package com.example.scheduler.chat.persistence;

import com.example.scheduler.chat.domain.Conversation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "app.chat.store", havingValue = "mongo")
public class MongoConversationStore implements ConversationStore {
    private final ConversationMongoRepository repository;

    public MongoConversationStore(ConversationMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Conversation save(Conversation conversation) {
        return repository.save(new ConversationDocument(conversation)).toDomain();
    }

    @Override
    public Optional<Conversation> findByIdAndOwnerId(UUID id, String ownerId) {
        return repository.findByIdAndOwnerId(id.toString(), ownerId).map(ConversationDocument::toDomain);
    }

    @Override
    public List<Conversation> findRecentByOwnerId(String ownerId, int limit) {
        return repository.findByOwnerIdOrderByUpdatedAtDesc(ownerId, PageRequest.of(0, limit)).stream()
                .map(ConversationDocument::toDomain).toList();
    }
}
