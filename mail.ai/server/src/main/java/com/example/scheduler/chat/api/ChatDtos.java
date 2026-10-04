package com.example.scheduler.chat.api;

import com.example.scheduler.chat.domain.ChatMessage;
import com.example.scheduler.chat.domain.Conversation;
import com.example.scheduler.chat.domain.ScheduleProposal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public final class ChatDtos {
    private ChatDtos() {}

    public record SendMessageRequest(@NotBlank @Size(max = 4000) String message, ZoneId timezone) {}
    public record ConversationSummary(UUID id, String title, Instant updatedAt, int messageCount) {
        public static ConversationSummary from(Conversation c) {
            return new ConversationSummary(c.id(), c.title(), c.updatedAt(), c.messages().size());
        }
    }
    public record ConversationView(UUID id, String title, List<ChatMessage> messages) {
        public static ConversationView from(Conversation c) {
            return new ConversationView(c.id(), c.title(), c.messages());
        }
    }
    public record ProposalView(UUID id, String status, List<ScheduleProposal.FixedEmail> emails,
                               ScheduleProposal.RecurringEmail recurrence, Instant expiresAt) {
        public static ProposalView from(ScheduleProposal p) {
            return new ProposalView(p.id(), p.status().name(), p.emails(), p.recurrence(), p.expiresAt());
        }
    }
}
