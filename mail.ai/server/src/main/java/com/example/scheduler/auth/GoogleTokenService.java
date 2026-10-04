package com.example.scheduler.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class GoogleTokenService {
    private final GoogleAccountRepository accounts;
    private final TokenEncryptionService encryption;
    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    public GoogleTokenService(GoogleAccountRepository accounts, TokenEncryptionService encryption,
                              RestClient.Builder builder,
                              @Value("${app.google.client-id:}") String clientId,
                              @Value("${app.google.client-secret:}") String clientSecret) {
        this.accounts = accounts;
        this.encryption = encryption;
        this.restClient = builder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String accessTokenFor(String ownerId) {
        GoogleAccount account = accounts.findById(ownerId)
                .orElseThrow(() -> new IllegalStateException("No Gmail account is connected for this user"));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", encryption.decrypt(account.getEncryptedRefreshToken()));
        form.add("grant_type", "refresh_token");
        Map<?, ?> response = restClient.post().uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
        Object token = response == null ? null : response.get("access_token");
        if (!(token instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("Google token refresh did not return an access token");
        }
        return value;
    }
}
