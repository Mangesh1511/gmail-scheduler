package com.example.scheduler.chat.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ConversationMongoRepository extends MongoRepository<ConversationDocument, String> {
    Optional<ConversationDocument> findByIdAndOwnerId(String id, String ownerId);
    List<ConversationDocument> findByOwnerIdOrderByUpdatedAtDesc(String ownerId, Pageable pageable);
}
