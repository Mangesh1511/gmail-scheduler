package com.example.scheduler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_jobs", indexes = @Index(name = "idx_jobs_schedule_due", columnList = "schedule_id,due_at"),
       uniqueConstraints = @UniqueConstraint(name = "uk_job_schedule_occurrence", columnNames = {"schedule_id", "due_at"}))
public class EmailJob {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobState state;

    private Instant createdAt;

    protected EmailJob() {}

    public EmailJob(Schedule schedule, Instant dueAt) {
        this.schedule = schedule;
        this.dueAt = dueAt;
        this.state = JobState.PENDING;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Schedule getSchedule() { return schedule; }
    public Instant getDueAt() { return dueAt; }
    public JobState getState() { return state; }
    public Instant getCreatedAt() { return createdAt; }
}
