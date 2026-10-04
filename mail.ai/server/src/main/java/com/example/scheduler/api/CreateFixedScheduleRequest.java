package com.example.scheduler.api;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateFixedScheduleRequest(
    @NotEmpty(message = "Add at least one recipient.")
    List<@NotBlank(message = "Recipient email is required.")
         @Email(message = "Enter a valid email address.") String> recipients,

    @NotBlank(message = "Subject is required.")
    String subject,

    @NotBlank(message = "Email body is required.")
    String body,

    @NotNull(message = "Send time is required.")
    Instant sendAt,

    @NotNull(message = "Timezone is required.")
    ZoneId timezone
) {}
