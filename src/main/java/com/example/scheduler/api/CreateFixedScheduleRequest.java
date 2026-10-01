package com.example.scheduler.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

public record CreateFixedScheduleRequest(
        @NotEmpty List<@Email String> recipients,
        @NotBlank String subject,
        @NotBlank String body,
        @NotNull Instant sendAt,
        @NotNull ZoneId timezone
) {}
