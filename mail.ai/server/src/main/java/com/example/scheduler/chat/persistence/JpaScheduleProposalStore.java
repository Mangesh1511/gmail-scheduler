package com.example.scheduler.chat.persistence;

import com.example.scheduler.chat.domain.ScheduleProposal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "app.chat.proposal-store", havingValue = "jpa", matchIfMissing = true)
public class JpaScheduleProposalStore implements ScheduleProposalStore {
    private final ScheduleProposalJpaRepository repository;
    private final ObjectMapper mapper;

    public JpaScheduleProposalStore(ScheduleProposalJpaRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public ScheduleProposal save(ScheduleProposal proposal) {
        try {
            String emails = mapper.writeValueAsString(proposal.emails());
            String recurrence = mapper.writeValueAsString(proposal.recurrence());
            String ids = mapper.writeValueAsString(proposal.createdScheduleIds());
            ScheduleProposalDocument existing = repository.findById(proposal.id()).orElse(null);
            if (existing == null) {
                return toDomain(repository.save(ScheduleProposalDocument.from(proposal, emails, recurrence, ids)));
            }
            existing.setStatus(proposal.status());
            existing.setEmailsJson(emails);
            existing.setRecurrenceJson(recurrence);
            existing.setCreatedScheduleIdsJson(ids);
            return toDomain(repository.save(existing));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not persist schedule proposal", exception);
        }
    }

    @Override
    public Optional<ScheduleProposal> findByIdAndOwnerId(UUID id, String ownerId) {
        return repository.findForUpdateByIdAndOwnerId(id, ownerId).map(this::toDomain);
    }

    @Override
    public Optional<ScheduleProposal> findLatestConfirmableByConversationIdAndOwnerId(UUID conversationId, String ownerId) {
        return repository.findLatestConfirmableForUpdate(conversationId, ownerId,
                        ScheduleProposal.ProposalStatus.READY, ScheduleProposal.ProposalStatus.CONFIRMED,
                        PageRequest.of(0, 1))
                .stream().findFirst().map(this::toDomain);
    }

    @Override
    public Optional<ScheduleProposal> findLatestByConversationIdAndOwnerId(UUID conversationId, String ownerId) {
        return repository.findFirstByConversationIdAndOwnerIdOrderByCreatedAtDesc(conversationId, ownerId)
                .map(this::toDomain);
    }

    private ScheduleProposal toDomain(ScheduleProposalDocument document) {
        try {
            List<ScheduleProposal.FixedEmail> emails = mapper.readValue(document.getEmailsJson(),
                    new TypeReference<>() { });
            ScheduleProposal.RecurringEmail recurrence = document.getRecurrenceJson() == null ? null
                    : mapper.readValue(document.getRecurrenceJson(), ScheduleProposal.RecurringEmail.class);
            List<UUID> scheduleIds = document.getCreatedScheduleIdsJson() == null ? List.of()
                    : mapper.readValue(document.getCreatedScheduleIdsJson(), new TypeReference<>() { });
            return new ScheduleProposal(document.getId(), document.getConversationId(), document.getOwnerId(),
                    document.getStatus(), emails, recurrence, document.getExpiresAt(), scheduleIds);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read schedule proposal", exception);
        }
    }
}
