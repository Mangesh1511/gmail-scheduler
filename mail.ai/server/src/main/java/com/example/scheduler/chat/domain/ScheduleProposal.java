package com.example.scheduler.chat.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public record ScheduleProposal(UUID id, UUID conversationId, String ownerId, ProposalStatus status,
                               List<FixedEmail> emails, RecurringEmail recurrence,
                               Instant expiresAt, List<UUID> createdScheduleIds) {
    public ScheduleProposal {
        emails = List.copyOf(emails);
        createdScheduleIds = List.copyOf(createdScheduleIds);
    }

    public record FixedEmail(List<String> recipients, String subject, String body,
                             Instant sendAt, ZoneId timezone) {
        public FixedEmail { recipients = List.copyOf(recipients); }
    }

    public record RecurringEmail(List<String> recipients, String subject, String body,
                                 Instant startAt, Instant endAt, long intervalSeconds, ZoneId timezone) {
        public RecurringEmail { recipients = List.copyOf(recipients); }
    }

    public enum ProposalStatus { READY, CONFIRMED, REJECTED, EXPIRED }
}
