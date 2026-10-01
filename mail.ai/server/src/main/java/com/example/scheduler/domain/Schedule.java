package com.example.scheduler.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "schedules", indexes = @Index(name = "idx_schedules_owner_created", columnList = "owner_id,created_at"))
public class Schedule {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleState state;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "schedule_recipients", joinColumns = @JoinColumn(name = "schedule_id"))
    @Column(name = "recipient", nullable = false)
    private List<String> recipients = new ArrayList<>();

    @Column(nullable = false, length = 1000)
    private String subject;

    @Column(nullable = false, length = 20000)
    private String body;

    @Column(nullable = false)
    private ZoneId timezone;

    private Instant startAt;
    private Instant endAt;
    private Long intervalSeconds;
    private Instant createdAt;

    protected Schedule() {}

    public Schedule(String ownerId, ScheduleType type, List<String> recipients, String subject,
                    String body, ZoneId timezone, Instant startAt, Instant endAt, Long intervalSeconds) {
        this.ownerId = ownerId;
        this.type = type;
        this.state = ScheduleState.ACTIVE;
        this.recipients = new ArrayList<>(recipients);
        this.subject = subject;
        this.body = body;
        this.timezone = timezone;
        this.startAt = startAt;
        this.endAt = endAt;
        this.intervalSeconds = intervalSeconds;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public ScheduleType getType() { return type; }
    public ScheduleState getState() { return state; }
    public List<String> getRecipients() { return List.copyOf(recipients); }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public ZoneId getTimezone() { return timezone; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public Long getIntervalSeconds() { return intervalSeconds; }
    public Instant getCreatedAt() { return createdAt; }
}
