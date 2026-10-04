package com.example.scheduler.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@RestController
public class GoogleOAuthController {
    private static final String STATE_ATTRIBUTE = "google_oauth_state";
    private static final String GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";
    private final RestClient restClient;
    private final GoogleAccountRepository accountRepository;
    private final TokenEncryptionService encryptionService;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String frontendBaseUrl;
    private final SecureRandom random = new SecureRandom();

    public GoogleOAuthController(RestClient.Builder restClientBuilder,
                                 GoogleAccountRepository accountRepository,
                                 TokenEncryptionService encryptionService,
                                 @Value("${app.google.client-id:}") String clientId,
                                 @Value("${app.google.client-secret:}") String clientSecret,
                                 @Value("${app.google.redirect-uri:http://localhost:8080/auth/google/callback}") String redirectUri,
                                 @Value("${app.frontend-base-url:http://localhost:4200}") String frontendBaseUrl) {
        this.restClient = restClientBuilder.build();
        this.accountRepository = accountRepository;
        this.encryptionService = encryptionService;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @GetMapping("/auth/google")
    public ResponseEntity<Void> beginGoogleConnection(HttpSession session) {
        requireConfigured();
        byte[] stateBytes = new byte[32];
        random.nextBytes(stateBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);
        session.setAttribute(STATE_ATTRIBUTE, state);
        java.net.URI url = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile " + GMAIL_SEND_SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("include_granted_scopes", "true")
                .queryParam("state", state)
                .build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(url).build();
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/auth/google/callback")
    public org.springframework.web.servlet.view.RedirectView completeGoogleConnection(@RequestParam String code,
                                                                                      @RequestParam String state,
                                                                                      HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object expected = session == null ? null : session.getAttribute(STATE_ATTRIBUTE);
        if (session != null) session.removeAttribute(STATE_ATTRIBUTE);
        if (!(expected instanceof String expectedState) || !expectedState.equals(state)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OAuth state did not match");
        }
        requireConfigured();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");
        Map<?, ?> tokens = restClient.post().uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
        String accessToken = required(tokens, "access_token");
        String refreshToken = (String) tokens.get("refresh_token");
        Map<?, ?> profile = restClient.get().uri("https://openidconnect.googleapis.com/v1/userinfo")
                .headers(headers -> headers.setBearerAuth(accessToken)).retrieve().body(Map.class);
        String ownerId = required(profile, "sub");
        String email = required(profile, "email");
        if (refreshToken != null && !refreshToken.isBlank()) {
            accountRepository.save(new GoogleAccount(ownerId, email, encryptionService.encrypt(refreshToken)));
        } else if (accountRepository.findById(ownerId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google did not return a refresh token; reconnect and approve offline access");
        }
        request.changeSessionId();
        request.getSession().setAttribute("ownerId", ownerId);
        request.getSession().setAttribute("googleEmail", email);
        return new org.springframework.web.servlet.view.RedirectView(
                frontendBaseUrl.replaceAll("/$", "") + "/chat?connected=true", true);
    }

    private void requireConfigured() {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google OAuth is not configured");
        }
    }

    private static String required(Map<?, ?> values, String key) {
        Object value = values == null ? null : values.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google OAuth response omitted " + key);
        }
        return text;
    }
}
