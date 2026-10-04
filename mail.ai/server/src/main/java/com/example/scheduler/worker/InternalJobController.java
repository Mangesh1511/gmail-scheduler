package com.example.scheduler.worker;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

@RestController
public class InternalJobController {
    private static final Logger log = LoggerFactory.getLogger(InternalJobController.class);
    private final QStashSignatureVerifier verifier;
    private final JobProcessor processor;

    public InternalJobController(QStashSignatureVerifier verifier, JobProcessor processor) {
        this.verifier = verifier;
        this.processor = processor;
    }

    @PostMapping("/internal/jobs/{jobId}/send")
    public ResponseEntity<Void> send(@PathVariable UUID jobId, HttpServletRequest request) throws IOException {
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        if (!verifier.verify(request.getHeader("Upstash-Signature"), body, jobId.toString())) {
            log.warn("Rejected QStash callback for job {} because its signature was invalid", jobId);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid QStash signature");
        }
        log.info("Accepted QStash callback for job {}", jobId);
        processor.process(jobId);
        return ResponseEntity.noContent().build();
    }
}
