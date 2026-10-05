package com.example.scheduler.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;
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
    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthController.class);
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
    public org.springframework.web.servlet.view.RedirectView completeGoogleConnection(
                                                                                      @RequestParam(required = false) String code,
                                                                                      @RequestParam(required = false) String state,
                                                                                      @RequestParam(required = false) String error,
                                                                                      HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object expected = session == null ? null : session.getAttribute(STATE_ATTRIBUTE);
        if (session != null) session.removeAttribute(STATE_ATTRIBUTE);
        if (!(expected instanceof String expectedState) || !expectedState.equals(state)) {
            log.warn("Google OAuth callback rejected: state mismatch; sessionPresent={}, expectedStatePresent={}, returnedStatePresent={}",
                    session != null, expected instanceof String, state != null && !state.isBlank());
            return frontendRedirect("state_mismatch");
        }
        if (error != null && !error.isBlank()) {
            log.info("Google OAuth consent was not granted; providerError={}", safeProviderError(error));
            return frontendRedirect("consent_denied");
        }
        if (code == null || code.isBlank()) {
            log.warn("Google OAuth callback contained neither an authorization code nor a provider error");
            return frontendRedirect("missing_code");
        }
        if (clientId.isBlank() || clientSecret.isBlank()) {
            log.error("Google OAuth callback could not be completed because OAuth credentials are not configured");
            return frontendRedirect("oauth_not_configured");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");
        Map<?, ?> tokens;
        try {
            tokens = restClient.post().uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
        } catch (RestClientResponseException exception) {
            log.error("Google OAuth code exchange failed; providerStatus={}", exception.getStatusCode().value());
            return frontendRedirect("token_exchange_failed");
        } catch (RuntimeException exception) {
            log.error("Google OAuth code exchange failed; exceptionType={}", exception.getClass().getSimpleName());
            return frontendRedirect("token_exchange_failed");
        }
        String accessToken = stringValue(tokens, "access_token");
        if (accessToken == null || accessToken.isBlank()) {
            log.error("Google OAuth token response did not include an access token");
            return frontendRedirect("token_exchange_failed");
        }
        String refreshToken = (String) tokens.get("refresh_token");
        Map<?, ?> profile;
        try {
            profile = restClient.get().uri("https://openidconnect.googleapis.com/v1/userinfo")
                    .headers(headers -> headers.setBearerAuth(accessToken)).retrieve().body(Map.class);
        } catch (RestClientResponseException exception) {
            log.error("Google user profile lookup failed; providerStatus={}", exception.getStatusCode().value());
            return frontendRedirect("profile_lookup_failed");
        } catch (RuntimeException exception) {
            log.error("Google user profile lookup failed; exceptionType={}", exception.getClass().getSimpleName());
            return frontendRedirect("profile_lookup_failed");
        }
        String ownerId = stringValue(profile, "sub");
        String email = stringValue(profile, "email");
        if (ownerId == null || ownerId.isBlank() || email == null || email.isBlank()) {
            log.error("Google user profile response omitted required identity fields");
            return frontendRedirect("profile_lookup_failed");
        }
        try {
            if (refreshToken != null && !refreshToken.isBlank()) {
                accountRepository.save(new GoogleAccount(ownerId, email, encryptionService.encrypt(refreshToken)));
            } else if (accountRepository.findById(ownerId).isEmpty()) {
                log.error("Google OAuth returned no refresh token and no stored account exists; ownerId={}", ownerId);
                return frontendRedirect("refresh_token_missing");
            }
        } catch (RuntimeException exception) {
            log.error("Google OAuth account credentials could not be stored; ownerId={}, exceptionType={}",
                    ownerId, exception.getClass().getSimpleName());
            return frontendRedirect("account_store_failed");
        }
        request.changeSessionId();
        request.getSession().setAttribute("ownerId", ownerId);
        request.getSession().setAttribute("googleEmail", email);
        return new org.springframework.web.servlet.view.RedirectView(
                frontendBaseUrl.replaceAll("/$", "") + "/chat?connected=true", true);
    }

    private org.springframework.web.servlet.view.RedirectView frontendRedirect(String errorCode) {
        String location = UriComponentsBuilder.fromUriString(frontendBaseUrl.replaceAll("/$", ""))
                .path("/chat")
                .queryParam("authError", errorCode)
                .build().encode().toUriString();
        return new org.springframework.web.servlet.view.RedirectView(location, true);
    }

    private static String safeProviderError(String error) {
        return switch (error) {
            case "access_denied" -> "access_denied";
            case "temporarily_unavailable" -> "temporarily_unavailable";
            case "server_error" -> "server_error";
            default -> "other";
        };
    }

    private void requireConfigured() {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google OAuth is not configured");
        }
    }

    private static String stringValue(Map<?, ?> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value instanceof String text ? text : null;
    }
}
