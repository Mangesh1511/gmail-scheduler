package com.example.scheduler.chat.persistence;

import com.example.scheduler.chat.domain.ChatMessage;
import com.example.scheduler.chat.domain.Conversation;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Document("conversations")
@CompoundIndex(name = "owner_updated", def = "{'ownerId': 1, 'updatedAt': -1}")
public class ConversationDocument {
    @Id
    private String id;
    private String ownerId;
    private String title;
    private List<ChatMessage> messages;
    private Instant createdAt;
    private Instant updatedAt;

    protected ConversationDocument() { }

    ConversationDocument(Conversation conversation) {
        id = conversation.id().toString();
        ownerId = conversation.ownerId();
        title = conversation.title();
        messages = conversation.messages();
        createdAt = conversation.createdAt();
        updatedAt = conversation.updatedAt();
    }

    Conversation toDomain() {
        return new Conversation(UUID.fromString(id), ownerId, title, messages, createdAt, updatedAt);
    }
}
