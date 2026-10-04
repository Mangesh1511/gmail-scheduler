package com.example.scheduler.worker;

import com.example.scheduler.domain.EmailJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;

@Service
public class QStashPublisher {
    private static final Logger log = LoggerFactory.getLogger(QStashPublisher.class);
    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String publishUrlPrefix;
    private final boolean enabled;

    public QStashPublisher(RestClient.Builder builder, ObjectMapper mapper,
                           @Value("${app.qstash.enabled:false}") boolean enabled,
                           @Value("${app.qstash.token:}") String token,
                           @Value("${app.qstash.url:https://qstash.upstash.io}") String qstashUrl,
                           @Value("${app.public-base-url:}") String baseUrl) {
        String normalizedQstashUrl = qstashUrl.replaceAll("/$", "");
        this.restClient = builder
                .baseUrl(normalizedQstashUrl)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
        this.mapper = mapper;
        this.enabled = enabled;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.publishUrlPrefix = normalizedQstashUrl + "/v2/publish/";
        log.info("QStash scheduling enabled={}, endpoint={}", enabled, normalizedQstashUrl);
        if (enabled && token.isBlank()) {
            log.error("QStash is enabled but QSTASH_TOKEN is empty");
        }
    }

    public void publish(EmailJob job) {
        if (!enabled) {
            log.warn("QStash is disabled; job {} was saved but no delivery was registered", job.getId());
            return;
        }
        if (baseUrl.isBlank()) throw new IllegalStateException("QStash requires a public base URL");
        String target = baseUrl + "/internal/jobs/" + job.getId() + "/send";
        String notBefore = Long.toString(job.getDueAt().getEpochSecond());
        String body;
        try { body = mapper.writeValueAsString(Map.of("jobId", job.getId().toString())); }
        catch (Exception exception) { throw new IllegalStateException("Unable to serialize QStash payload", exception); }
        try {
            // QStash expects the destination URL as the raw suffix of this route.
            // Passing a URI (rather than a URI template string) preserves the nested
            // http:// scheme and slashes in that suffix.
            URI publishUri = URI.create(publishUrlPrefix + target);
            restClient.post().uri(publishUri)
                    .header("Upstash-Not-Before", notBefore)
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
            log.info("Registered QStash delivery for job {} at {}", job.getId(), job.getDueAt());
        } catch (RuntimeException exception) {
            log.error("Could not register QStash delivery for job {} at {}", job.getId(), job.getDueAt(), exception);
            throw exception;
        }
    }
}
