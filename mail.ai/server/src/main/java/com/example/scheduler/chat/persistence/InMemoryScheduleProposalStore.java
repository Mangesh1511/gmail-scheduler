package com.example.scheduler.chat.persistence;

import com.example.scheduler.chat.domain.ScheduleProposal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "app.chat.proposal-store", havingValue = "memory")
public class InMemoryScheduleProposalStore implements ScheduleProposalStore {
    private final ConcurrentHashMap<UUID, ScheduleProposal> proposals = new ConcurrentHashMap<>();

    @Override
    public ScheduleProposal save(ScheduleProposal proposal) {
        proposals.put(proposal.id(), proposal);
        return proposal;
    }

    @Override
    public Optional<ScheduleProposal> findByIdAndOwnerId(UUID id, String ownerId) {
        return Optional.ofNullable(proposals.get(id)).filter(p -> p.ownerId().equals(ownerId));
    }

    @Override
    public Optional<ScheduleProposal> findLatestConfirmableByConversationIdAndOwnerId(UUID conversationId, String ownerId) {
        return proposals.values().stream()
                .filter(proposal -> proposal.conversationId().equals(conversationId)
                        && proposal.ownerId().equals(ownerId)
                        && (proposal.status() == ScheduleProposal.ProposalStatus.READY
                            || proposal.status() == ScheduleProposal.ProposalStatus.CONFIRMED))
                .max(Comparator.comparing(ScheduleProposal::expiresAt));
    }

    @Override
    public Optional<ScheduleProposal> findLatestByConversationIdAndOwnerId(UUID conversationId, String ownerId) {
        return proposals.values().stream()
                .filter(proposal -> proposal.conversationId().equals(conversationId)
                        && proposal.ownerId().equals(ownerId))
                .max(Comparator.comparing(ScheduleProposal::expiresAt));
    }
}
