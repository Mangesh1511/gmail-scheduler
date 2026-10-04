package com.example.scheduler.worker;

import com.example.scheduler.domain.EmailJob;
import com.example.scheduler.domain.EmailJobRepository;
import com.example.scheduler.mail.GmailMessageSender;
import com.example.scheduler.mail.OutboundMail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class JobProcessor {
    private static final Logger log = LoggerFactory.getLogger(JobProcessor.class);
    private final EmailJobRepository jobs;
    private final GmailMessageSender sender;

    public JobProcessor(EmailJobRepository jobs, GmailMessageSender sender) {
        this.jobs = jobs;
        this.sender = sender;
    }

    @Transactional
    public boolean process(UUID jobId) {
        if (jobs.claimDueJob(jobId) != 1) {
            log.warn("Skipped QStash callback for job {}; it was already claimed, is not due, or its schedule is inactive", jobId);
            return false;
        }
        EmailJob job = jobs.findByIdWithSchedule(jobId).orElseThrow();
        job.markStarted();
        try {
            String messageId = sender.send(new OutboundMail(job.getSchedule().getOwnerId(),
                    job.getSchedule().getRecipients(), job.getSchedule().getSubject(), job.getSchedule().getBody()));
            job.sent(messageId);
            log.info("Email job {} sent successfully; Gmail message ID {}", jobId, messageId);
        } catch (RuntimeException exception) {
            job.failed(exception.getMessage());
            log.error("Email job {} failed while sending through Gmail", jobId, exception);
        }
        return true;
    }
}
