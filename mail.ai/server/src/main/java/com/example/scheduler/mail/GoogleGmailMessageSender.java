package com.example.scheduler.mail;

import com.example.scheduler.auth.GoogleAccount;
import com.example.scheduler.auth.GoogleAccountRepository;
import com.example.scheduler.auth.GoogleTokenService;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;

@Service
public class GoogleGmailMessageSender implements GmailMessageSender {
    private final GoogleTokenService tokenService;
    private final GoogleAccountRepository accountRepository;
    private final RestClient restClient;

    public GoogleGmailMessageSender(GoogleTokenService tokenService, GoogleAccountRepository accountRepository,
                                   RestClient.Builder builder) {
        this.tokenService = tokenService;
        this.accountRepository = accountRepository;
        this.restClient = builder.build();
    }

    @Override
    public String send(OutboundMail mail) {
        try {
            GoogleAccount account = accountRepository.findById(mail.ownerId())
                    .orElseThrow(() -> new IllegalStateException("No Gmail account is connected for this user"));
            MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
            mime.setFrom(new InternetAddress(account.getEmail()));
            for (String recipient : mail.recipients()) {
                mime.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient, true));
            }
            mime.setSubject(mail.subject(), "UTF-8");
            mime.setText(mail.body(), "UTF-8");
            mime.saveChanges();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            mime.writeTo(bytes);
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
            Map<?, ?> response = restClient.post()
                    .uri("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
                    .headers(headers -> headers.setBearerAuth(tokenService.accessTokenFor(mail.ownerId())))
                    .body(Map.of("raw", raw))
                    .retrieve().body(Map.class);
            Object messageId = response == null ? null : response.get("id");
            if (!(messageId instanceof String id)) throw new IllegalStateException("Gmail API response omitted message ID");
            return id;
        } catch (Exception exception) {
            throw new GmailSendException("Gmail send failed", exception);
        }
    }
}
