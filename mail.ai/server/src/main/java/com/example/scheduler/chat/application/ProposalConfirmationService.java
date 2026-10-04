package com.example.scheduler.chat.application;

import com.example.scheduler.api.CreateFixedScheduleRequest;
import com.example.scheduler.api.CreateRecurrenceScheduleRequest;
import com.example.scheduler.api.ScheduleResponse;
import com.example.scheduler.chat.domain.ScheduleProposal;
import com.example.scheduler.chat.persistence.ScheduleProposalStore;
import com.example.scheduler.service.ScheduleApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Service
public class ProposalConfirmationService {
    private static final Logger log = LoggerFactory.getLogger(ProposalConfirmationService.class);
    private final ScheduleProposalStore proposals;
    private final ScheduleApplicationService schedules;

    public ProposalConfirmationService(ScheduleProposalStore proposals, ScheduleApplicationService schedules) {
        this.proposals = proposals;
        this.schedules = schedules;
    }

    @Transactional
    public ConfirmationResponse confirm(UUID proposalId, String ownerId) {
        log.info("Proposal confirmation requested; proposalId={}", proposalId);
        ScheduleProposal proposal = proposals.findByIdAndOwnerId(proposalId, ownerId).orElseThrow(() -> {
            log.warn("Proposal confirmation failed because proposal was not found or not owned by caller; proposalId={}", proposalId);
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found");
        });
        return confirmLoaded(proposal, ownerId);
    }

    @Transactional
    public Optional<ConfirmationResponse> confirmLatestReady(UUID conversationId, String ownerId) {
        log.info("Verbal proposal confirmation requested; conversationId={}", conversationId);
        return proposals.findLatestConfirmableByConversationIdAndOwnerId(conversationId, ownerId)
                .flatMap(proposal -> {
                    if (proposal.status() == ScheduleProposal.ProposalStatus.READY
                            && !proposal.expiresAt().isAfter(Instant.now())) return Optional.empty();
                    return Optional.of(confirmLoaded(proposal, ownerId));
                });
    }

    public String latestProposalContext(UUID conversationId, String ownerId) {
        return proposals.findLatestByConversationIdAndOwnerId(conversationId, ownerId)
                .map(proposal -> switch (proposal.status()) {
                    case CONFIRMED -> "The latest email schedule proposal is confirmed; the schedule was created successfully.";
                    case READY -> proposal.expiresAt().isAfter(Instant.now())
                            ? "The latest email schedule proposal is awaiting confirmation."
                            : "The latest email schedule proposal has expired and cannot be confirmed.";
                    case EXPIRED -> "The latest email schedule proposal has expired and cannot be confirmed.";
                    case REJECTED -> "The latest email schedule proposal was rejected and was not scheduled.";
                })
                .orElse("There is no saved email schedule proposal in this conversation.");
    }

    private ConfirmationResponse confirmLoaded(ScheduleProposal proposal, String ownerId) {
        UUID proposalId = proposal.id();
        log.info("Proposal loaded for confirmation; proposalId={}, status={}, expired={}, fixedEmails={}, recurrence={}",
                proposalId, proposal.status(), proposal.expiresAt().isBefore(Instant.now()),
                proposal.emails().size(), proposal.recurrence() != null);
        if (proposal.status() == ScheduleProposal.ProposalStatus.CONFIRMED) {
            log.info("Proposal was already confirmed; proposalId={}, scheduleCount={}", proposalId,
                    proposal.createdScheduleIds().size());
            return new ConfirmationResponse(proposal.createdScheduleIds(), true, proposal.conversationId());
        }
        if (proposal.status() != ScheduleProposal.ProposalStatus.READY || proposal.expiresAt().isBefore(Instant.now())) {
            log.warn("Proposal confirmation rejected because proposal is no longer ready; proposalId={}, status={}",
                    proposalId, proposal.status());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Proposal is expired or already used");
        }

        List<ScheduleResponse> created;
        try {
            log.info("Creating schedule(s) from proposal; proposalId={}", proposalId);
            created = createSchedules(ownerId, proposal);
        } catch (RuntimeException exception) {
            log.error("Schedule creation failed for proposal; proposalId={}, exceptionType={}",
                    proposalId, exception.getClass().getSimpleName(), exception);
            throw exception;
        }
        proposals.save(new ScheduleProposal(proposal.id(), proposal.conversationId(), ownerId,
                ScheduleProposal.ProposalStatus.CONFIRMED, proposal.emails(), proposal.recurrence(), proposal.expiresAt(),
                created.stream().map(ScheduleResponse::id).toList()));
        log.info("Proposal confirmed and schedule(s) created; proposalId={}, scheduleCount={}, scheduleIds={}",
                proposalId, created.size(), created.stream().map(ScheduleResponse::id).toList());
        return new ConfirmationResponse(created.stream().map(ScheduleResponse::id).toList(), false,
                proposal.conversationId());
    }

    private List<ScheduleResponse> createSchedules(String ownerId, ScheduleProposal proposal) {
        List<ScheduleResponse> created = new ArrayList<>();
        if (proposal.recurrence() != null) {
            var recurrence = proposal.recurrence();
            created.add(schedules.createRecurrence(ownerId, new CreateRecurrenceScheduleRequest(recurrence.recipients(),
                    recurrence.subject(), recurrence.body(), recurrence.startAt(), recurrence.endAt(),
                    recurrence.intervalSeconds(), recurrence.timezone())));
            return created;
        }
        for (var email : proposal.emails()) {
            created.add(schedules.createFixed(ownerId, new CreateFixedScheduleRequest(email.recipients(), email.subject(),
                    email.body(), email.sendAt(), email.timezone())));
        }
        return created;
    }

    public record ConfirmationResponse(List<UUID> scheduleIds, boolean alreadyConfirmed, UUID conversationId) {
        public ConfirmationResponse { scheduleIds = List.copyOf(scheduleIds); }
    }
}
