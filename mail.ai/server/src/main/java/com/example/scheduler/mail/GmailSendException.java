package com.example.scheduler.mail;

public class GmailSendException extends RuntimeException {
    public GmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
