package com.example.scheduler.worker;

import com.example.scheduler.domain.EmailJob;
import com.example.scheduler.domain.EmailJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

@Component
public class JobSchedulingListener {
    private static final Logger log = LoggerFactory.getLogger(JobSchedulingListener.class);
    private final EmailJobRepository jobs;
    private final QStashPublisher publisher;

    public JobSchedulingListener(EmailJobRepository jobs, QStashPublisher publisher) {
        this.jobs = jobs;
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void scheduleJobs(JobsCreated event) {
        log.info("Registering {} scheduled job(s) with QStash", event.jobIds().size());
        for (UUID id : event.jobIds()) {
            try {
                EmailJob job = jobs.findById(id).orElseThrow(() -> new IllegalStateException("Email job not found after transaction commit"));
                log.info("Registering job with QStash; jobId={}, dueAt={}", id, job.getDueAt());
                publisher.publish(job);
            } catch (RuntimeException exception) {
                log.error("QStash job registration failed after database commit; jobId={}, exceptionType={}",
                        id, exception.getClass().getSimpleName(), exception);
                throw exception;
            }
        }
    }

    public record JobsCreated(List<UUID> jobIds) { }
}
