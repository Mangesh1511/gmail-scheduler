package com.example.scheduler.api;

import com.example.scheduler.domain.Schedule;
import com.example.scheduler.domain.ScheduleState;
import com.example.scheduler.domain.ScheduleType;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public record ScheduleResponse(
        UUID id,
        ScheduleType type,
        ScheduleState state,
        List<String> recipients,
        String subject,
        String body,
        ZoneId timezone,
        Instant startAt,
        Instant endAt,
        Long intervalSeconds,
        Instant createdAt
) {
    public static ScheduleResponse from(Schedule schedule) {
        return new ScheduleResponse(schedule.getId(), schedule.getType(), schedule.getState(),
                schedule.getRecipients(), schedule.getSubject(), schedule.getBody(), schedule.getTimezone(),
                schedule.getStartAt(), schedule.getEndAt(), schedule.getIntervalSeconds(),
                schedule.getCreatedAt());
    }
}
