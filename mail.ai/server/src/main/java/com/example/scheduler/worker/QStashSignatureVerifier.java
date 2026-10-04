package com.example.scheduler.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Component
public class QStashSignatureVerifier {
    private final ObjectMapper mapper;
    private final String currentKey;
    private final String nextKey;
    private final String expectedUrl;

    public QStashSignatureVerifier(ObjectMapper mapper,
                                   @Value("${app.qstash.current-signing-key:}") String currentKey,
                                   @Value("${app.qstash.next-signing-key:}") String nextKey,
                                   @Value("${app.public-base-url:}") String publicBaseUrl) {
        this.mapper = mapper;
        this.currentKey = currentKey;
        this.nextKey = nextKey;
        this.expectedUrl = publicBaseUrl.isBlank() ? "" : publicBaseUrl.replaceAll("/$", "") + "/internal/jobs/";
    }

    public boolean verify(String jwt, byte[] body, String jobId) {
        try {
            if (currentKey.isBlank() || expectedUrl.isBlank() || jwt == null) return false;
            String[] parts = jwt.split("\\.");
            if (parts.length != 3) return false;
            Map<?, ?> header = mapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Map.class);
            Map<?, ?> claims = mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            if (!"HS256".equals(header.get("alg")) || !"Upstash".equals(claims.get("iss"))) return false;
            String subject = String.valueOf(claims.get("sub"));
            if (!subject.equals(expectedUrl + jobId + "/send")) return false;
            long now = Instant.now().getEpochSecond();
            if (!claims.containsKey("exp") || !claims.containsKey("nbf")
                    || number(claims.get("exp")) < now || number(claims.get("nbf")) > now + 30) return false;
            String expectedBodyHash = Base64.getUrlEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(body));
            if (!expectedBodyHash.equals(claims.get("body"))) return false;
            String signed = parts[0] + "." + parts[1];
            return validSignature(currentKey, signed, parts[2]) || (!nextKey.isBlank() && validSignature(nextKey, signed, parts[2]));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static long number(Object value) {
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private static boolean validSignature(String secret, String data, String signature) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        // QStash signing keys are opaque strings (including the "sig_" prefix),
        // not Base64-encoded key material. HMAC must use the exact configured value.
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] actual = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        byte[] supplied = Base64.getUrlDecoder().decode(signature);
        return MessageDigest.isEqual(actual, supplied);
    }
}
