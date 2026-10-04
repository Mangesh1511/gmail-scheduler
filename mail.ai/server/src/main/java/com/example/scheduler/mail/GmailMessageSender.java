package com.example.scheduler.mail;

public interface GmailMessageSender {
    String send(OutboundMail mail);
}
