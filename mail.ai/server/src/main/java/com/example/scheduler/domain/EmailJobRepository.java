package com.example.scheduler.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmailJobRepository extends JpaRepository<EmailJob, UUID> {
    List<EmailJob> findByScheduleIdOrderByDueAtAsc(UUID scheduleId);
}
