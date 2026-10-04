package com.example.scheduler.chat.persistence;

import com.example.scheduler.chat.domain.ScheduleProposal;
import com.example.scheduler.chat.domain.ScheduleProposal.ProposalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "schedule_proposals", indexes = {
        @Index(name = "idx_proposals_owner_created", columnList = "owner_id,created_at"),
        @Index(name = "idx_proposals_conversation", columnList = "conversation_id")
})
public class ScheduleProposalDocument {
    @Id
    private UUID id;
    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;
    @Column(name = "owner_id", nullable = false)
    private String ownerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalStatus status;
    @Column(name = "proposal_json", nullable = false, columnDefinition = "TEXT")
    private String emailsJson;
    @Column(name = "recurrence_json", columnDefinition = "TEXT")
    private String recurrenceJson;
    @Column(name = "created_schedule_ids", columnDefinition = "TEXT")
    private String createdScheduleIdsJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    private Instant confirmedAt;
    @Version
    private long version;

    protected ScheduleProposalDocument() { }

    public UUID getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public String getOwnerId() { return ownerId; }
    public ProposalStatus getStatus() { return status; }
    public String getEmailsJson() { return emailsJson; }
    public String getRecurrenceJson() { return recurrenceJson; }
    public String getCreatedScheduleIdsJson() { return createdScheduleIdsJson; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setStatus(ProposalStatus status) { this.status = status; }
    public void setEmailsJson(String value) { emailsJson = value; }
    public void setRecurrenceJson(String value) { recurrenceJson = value; }
    public void setCreatedScheduleIdsJson(String value) { createdScheduleIdsJson = value; }
    public void setConfirmedAt(Instant value) { confirmedAt = value; }

    static ScheduleProposalDocument from(ScheduleProposal proposal, String emailsJson, String recurrenceJson, String idsJson) {
        var doc = new ScheduleProposalDocument();
        doc.id = proposal.id();
        doc.conversationId = proposal.conversationId();
        doc.ownerId = proposal.ownerId();
        doc.status = proposal.status();
        doc.emailsJson = emailsJson;
        doc.recurrenceJson = recurrenceJson;
        doc.createdScheduleIdsJson = idsJson;
        doc.createdAt = Instant.now();
        doc.expiresAt = proposal.expiresAt();
        doc.confirmedAt = proposal.status() == ProposalStatus.CONFIRMED ? Instant.now() : null;
        return doc;
    }
}
