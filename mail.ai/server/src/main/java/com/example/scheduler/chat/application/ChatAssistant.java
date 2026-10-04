package com.example.scheduler.chat.application;

import com.example.scheduler.chat.domain.ChatMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.ZoneId;
import java.util.List;

/** Application-owned port for natural-language chat and schedule intent extraction. */
public interface ChatAssistant {
    boolean isConfigured();
    Flux<String> streamReply(List<ChatMessage> history, String userText, ZoneId timezone, String applicationState);
    Mono<AssistantTurn> classify(List<ChatMessage> history, String userText, String assistantReply,
                                 ZoneId timezone, String applicationState);

    record AssistantTurn(String kind, String reply, ProposedSchedule proposal) { }
    record ProposedSchedule(List<ProposedEmail> emails, ProposedRecurrence recurrence) { }
    record ProposedEmail(List<String> recipients, String subject, String body, String sendAt, String timezone) { }
    record ProposedRecurrence(List<String> recipients, String subject, String body, String startAt,
                              String endAt, long intervalSeconds, String timezone) { }
}
