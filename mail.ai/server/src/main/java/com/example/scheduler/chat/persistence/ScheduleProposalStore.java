package com.example.scheduler.chat.persistence;

import com.example.scheduler.chat.domain.ScheduleProposal;

import java.util.Optional;
import java.util.UUID;

public interface ScheduleProposalStore {
    ScheduleProposal save(ScheduleProposal proposal);
    Optional<ScheduleProposal> findByIdAndOwnerId(UUID id, String ownerId);
    Optional<ScheduleProposal> findLatestConfirmableByConversationIdAndOwnerId(UUID conversationId, String ownerId);
    Optional<ScheduleProposal> findLatestByConversationIdAndOwnerId(UUID conversationId, String ownerId);
}
