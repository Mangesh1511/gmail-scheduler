package com.example.scheduler.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

public record CreateRecurrenceScheduleRequest(
        @NotEmpty(message = "Add at least one recipient.")
        List<@NotBlank(message = "Recipient email is required.")
             @Email(message = "Enter a valid email address.") String> recipients,
        @NotBlank(message = "Subject is required.") String subject,
        @NotBlank(message = "Email body is required.") String body,
        @NotNull(message = "Start time is required.") Instant startAt,
        @NotNull(message = "End time is required.") Instant endAt,
        @Min(value = 3600, message = "Recurrence interval must be at least 3600 seconds (one hour).")
        long intervalSeconds,
        @NotNull(message = "Timezone is required.") ZoneId timezone
) {}
