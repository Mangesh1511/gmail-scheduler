package com.example.scheduler.mail;

import java.util.List;

public record OutboundMail(String ownerId, List<String> recipients, String subject, String body) {
}
