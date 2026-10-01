package com.example.scheduler.api;

import com.example.scheduler.domain.EmailJob;
import com.example.scheduler.domain.JobState;

import java.time.Instant;
import java.util.UUID;

public record JobResponse(UUID id, Instant dueAt, JobState state) {
    public static JobResponse from(EmailJob job) {
        return new JobResponse(job.getId(), job.getDueAt(), job.getState());
    }
}
