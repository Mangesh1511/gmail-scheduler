package com.example.scheduler.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailJobRepository extends JpaRepository<EmailJob, UUID> {
    List<EmailJob> findByScheduleIdOrderByDueAtAsc(UUID scheduleId);

    @Query("select job from EmailJob job join fetch job.schedule where job.id = :id")
    Optional<EmailJob> findByIdWithSchedule(@Param("id") UUID id);

    @Query("select job from EmailJob job join fetch job.schedule where job.schedule.ownerId = :ownerId order by job.dueAt asc")
    List<EmailJob> findByOwnerIdOrderByDueAtAsc(@Param("ownerId") String ownerId);
}
