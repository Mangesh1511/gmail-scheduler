package com.example.scheduler.api;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {
    private final JdbcTemplate jdbcTemplate;
    private final MongoTemplate mongoTemplate;
    private final boolean mongoRequired;

    public HealthController(JdbcTemplate jdbcTemplate, MongoTemplate mongoTemplate,
                            @Value("${app.chat.store:memory}") String chatStore) {
        this.jdbcTemplate = jdbcTemplate;
        this.mongoTemplate = mongoTemplate;
        this.mongoRequired = "mongo".equalsIgnoreCase(chatStore);
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean databaseUp;
        try {
            jdbcTemplate.execute("""
                    SELECT 1
                    """);
            databaseUp = true;
        } catch (Exception ignored) {
            databaseUp = false;
        }

        boolean mongoUp = true;
        if (mongoRequired) {
            try {
                mongoTemplate.executeCommand(new Document("ping", 1));
            } catch (Exception ignored) {
                mongoUp = false;
            }
        }

        Map<String, Object> body = Map.of(
                "status", databaseUp && mongoUp ? "UP" : "DOWN",
                "database", databaseUp ? "UP" : "DOWN",
                "mongodb", mongoRequired ? (mongoUp ? "UP" : "DOWN") : "NOT_REQUIRED",
                "timestamp", Instant.now().toString()
        );
        return ResponseEntity.status(databaseUp && mongoUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
