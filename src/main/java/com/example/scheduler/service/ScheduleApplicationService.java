package com.example.scheduler.service;

import com.example.scheduler.api.CreateFixedScheduleRequest;
import com.example.scheduler.api.CreateRecurrenceScheduleRequest;
import com.example.scheduler.api.JobResponse;
import com.example.scheduler.api.ScheduleResponse;
import com.example.scheduler.domain.EmailJob;
import com.example.scheduler.domain.EmailJobRepository;
import com.example.scheduler.domain.Schedule;
import com.example.scheduler.domain.ScheduleRepository;
import com.example.scheduler.domain.ScheduleType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class ScheduleApplicationService {
    private static final Duration MAX_HORIZON = Duration.ofDays(7);

    private final ScheduleRepository scheduleRepository;
    private final EmailJobRepository jobRepository;

    public ScheduleApplicationService(ScheduleRepository scheduleRepository, EmailJobRepository jobRepository) {
        this.scheduleRepository = scheduleRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public ScheduleResponse createFixed(String ownerId, CreateFixedScheduleRequest request) {
        Instant now = Instant.now();
        validateFutureWithinHorizon(request.sendAt(), now);
        Schedule schedule = scheduleRepository.save(new Schedule(ownerId, ScheduleType.FIXED,
                request.recipients(), request.subject(), request.body(), request.timezone(),
                request.sendAt(), request.sendAt(), null));
        EmailJob job = jobRepository.save(new EmailJob(schedule, request.sendAt()));
        return ScheduleResponse.from(schedule, List.of(JobResponse.from(job)));
    }

    @Transactional
    public ScheduleResponse createRecurrence(String ownerId, CreateRecurrenceScheduleRequest request) {
        Instant now = Instant.now();
        if (request.startAt().isBefore(now)) throw new InvalidScheduleException("startAt must be in the future");
        if (!request.endAt().isAfter(request.startAt())) throw new InvalidScheduleException("endAt must be after startAt");
        if (request.endAt().isAfter(now.plus(MAX_HORIZON))) throw new InvalidScheduleException("endAt cannot be more than 7 days away");
        if (request.intervalSeconds() < 3600) throw new InvalidScheduleException("recurrence cannot be more frequent than hourly");

        Schedule schedule = scheduleRepository.save(new Schedule(ownerId, ScheduleType.RECURRENCE,
                request.recipients(), request.subject(), request.body(), request.timezone(),
                request.startAt(), request.endAt(), request.intervalSeconds()));

        List<EmailJob> jobs = generateJobs(schedule, request.startAt(), request.endAt(), request.intervalSeconds());
        List<EmailJob> savedJobs = jobRepository.saveAll(jobs);
        return ScheduleResponse.from(schedule, savedJobs.stream().map(JobResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> listSchedules(String ownerId) {
        return scheduleRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(schedule -> ScheduleResponse.from(schedule,
                        jobRepository.findByScheduleIdOrderByDueAtAsc(schedule.getId()).stream()
                                .map(JobResponse::from).toList()))
                .toList();
    }

    private List<EmailJob> generateJobs(Schedule schedule, Instant startAt, Instant endAt, long intervalSeconds) {
        long count = Duration.between(startAt, endAt).getSeconds() / intervalSeconds + 1;
        if (count > 168) throw new InvalidScheduleException("schedule cannot create more than 168 jobs");
        return java.util.stream.LongStream.range(0, count)
                .mapToObj(index -> new EmailJob(schedule, startAt.plusSeconds(index * intervalSeconds)))
                .toList();
    }

    private void validateFutureWithinHorizon(Instant sendAt, Instant now) {
        if (!sendAt.isAfter(now)) throw new InvalidScheduleException("sendAt must be in the future");
        if (sendAt.isAfter(now.plus(MAX_HORIZON))) throw new InvalidScheduleException("sendAt cannot be more than 7 days away");
    }
}
