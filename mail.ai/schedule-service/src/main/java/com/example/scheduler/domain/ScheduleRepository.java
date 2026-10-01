package com.example.scheduler.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {
    List<Schedule> findByOwnerIdOrderByCreatedAtDesc(String ownerId);
}
