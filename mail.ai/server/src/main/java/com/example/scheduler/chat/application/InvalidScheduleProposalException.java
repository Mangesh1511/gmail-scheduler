package com.example.scheduler.chat.application;

public class InvalidScheduleProposalException extends RuntimeException {
    public InvalidScheduleProposalException(String message) {
        super(message);
    }
}
