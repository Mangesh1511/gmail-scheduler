package com.example.scheduler.chat.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleProposalJpaRepository extends JpaRepository<ScheduleProposalDocument, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT proposal
            FROM ScheduleProposalDocument proposal
            WHERE proposal.id = :id
              AND proposal.ownerId = :ownerId
            """)
    Optional<ScheduleProposalDocument> findForUpdateByIdAndOwnerId(@Param("id") UUID id,
                                                                   @Param("ownerId") String ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT proposal
            FROM ScheduleProposalDocument proposal
            WHERE proposal.conversationId = :conversationId
              AND proposal.ownerId = :ownerId
              AND (proposal.status = :ready OR proposal.status = :confirmed)
            ORDER BY proposal.createdAt DESC
            """)
    List<ScheduleProposalDocument> findLatestConfirmableForUpdate(@Param("conversationId") UUID conversationId,
                                                                  @Param("ownerId") String ownerId,
                                                                  @Param("ready") com.example.scheduler.chat.domain.ScheduleProposal.ProposalStatus ready,
                                                                  @Param("confirmed") com.example.scheduler.chat.domain.ScheduleProposal.ProposalStatus confirmed,
                                                                  Pageable pageable);

    Optional<ScheduleProposalDocument> findFirstByConversationIdAndOwnerIdOrderByCreatedAtDesc(UUID conversationId,
                                                                                                 String ownerId);
}
