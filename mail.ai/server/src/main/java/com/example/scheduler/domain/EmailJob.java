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

    private Instant startedAt;
    private Instant completedAt;

    @Column(length = 2000)
    private String lastError;

    private String gmailMessageId;

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
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getLastError() { return lastError; }
    public String getGmailMessageId() { return gmailMessageId; }

    public void markStarted() { startedAt = Instant.now(); }

    public boolean claim() {
        if (state != JobState.PENDING || dueAt.isAfter(Instant.now()) || schedule.getState() != ScheduleState.ACTIVE) return false;
        state = JobState.SENDING;
        startedAt = Instant.now();
        return true;
    }

    public void sent(String messageId) {
        state = JobState.SENT;
        gmailMessageId = messageId;
        completedAt = Instant.now();
    }

    public void failed(String error) {
        state = JobState.FAILED;
        lastError = error == null ? "Unknown send failure" : error.substring(0, Math.min(error.length(), 2000));
        completedAt = Instant.now();
    }
}
