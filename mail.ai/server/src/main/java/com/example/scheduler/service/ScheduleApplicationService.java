package com.example.scheduler.service;

import com.example.scheduler.api.CreateFixedScheduleRequest;
import com.example.scheduler.api.CreateRecurrenceScheduleRequest;
import com.example.scheduler.api.ScheduleResponse;
import com.example.scheduler.domain.EmailJob;
import com.example.scheduler.domain.EmailJobRepository;
import com.example.scheduler.domain.Schedule;
import com.example.scheduler.domain.ScheduleRepository;
import com.example.scheduler.domain.ScheduleType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.example.scheduler.worker.JobSchedulingListener.JobsCreated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class ScheduleApplicationService {
    private static final Logger log = LoggerFactory.getLogger(ScheduleApplicationService.class);
    private static final Duration MAX_HORIZON = Duration.ofDays(7);

    private final ScheduleRepository scheduleRepository;
    private final EmailJobRepository jobRepository;
    private final ApplicationEventPublisher events;

    public ScheduleApplicationService(ScheduleRepository scheduleRepository, EmailJobRepository jobRepository,
                                      ApplicationEventPublisher events) {
        this.scheduleRepository = scheduleRepository;
        this.jobRepository = jobRepository;
        this.events = events;
    }

    @Transactional
    public ScheduleResponse createFixed(String ownerId, CreateFixedScheduleRequest request) {
        log.info("Creating fixed schedule; recipientCount={}, sendAt={}, timezone={}",
                request.recipients().size(), request.sendAt(), request.timezone());
        Instant now = Instant.now();
        validateFutureWithinHorizon(request.sendAt(), now);
        Schedule schedule = new Schedule(ownerId, ScheduleType.FIXED,
                request.recipients(), request.subject(), request.body(), request.timezone(),
                request.sendAt(), request.sendAt(), null);
        Schedule newSchedule = scheduleRepository.save(schedule);
        EmailJob job = jobRepository.save(new EmailJob(newSchedule, request.sendAt()));
        log.info("Fixed schedule and email job persisted; scheduleId={}, jobId={}", newSchedule.getId(), job.getId());
        events.publishEvent(new JobsCreated(List.of(job.getId())));
        return ScheduleResponse.from(newSchedule);
    }

    @Transactional
    public ScheduleResponse createRecurrence(String ownerId, CreateRecurrenceScheduleRequest request) {
        log.info("Creating recurring schedule; recipientCount={}, startAt={}, endAt={}, intervalSeconds={}, timezone={}",
                request.recipients().size(), request.startAt(), request.endAt(), request.intervalSeconds(), request.timezone());
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
        log.info("Recurring schedule and email jobs persisted; scheduleId={}, jobCount={}", schedule.getId(), savedJobs.size());
        events.publishEvent(new JobsCreated(savedJobs.stream().map(EmailJob::getId).toList()));
        return ScheduleResponse.from(schedule);
    }

    public List<ScheduleResponse> listSchedules(String ownerId) {
        return scheduleRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(ScheduleResponse::from)
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
    }
}
