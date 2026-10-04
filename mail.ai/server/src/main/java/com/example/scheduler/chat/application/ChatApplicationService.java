package com.example.scheduler.chat.application;

import com.example.scheduler.chat.domain.ChatMessage;
import com.example.scheduler.chat.domain.Conversation;
import com.example.scheduler.chat.domain.ScheduleProposal;
import com.example.scheduler.chat.persistence.ConversationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ChatApplicationService {
    private static final Logger log = LoggerFactory.getLogger(ChatApplicationService.class);
    private final ConversationStore conversations;
    private final ChatAssistant assistant;
    private final ScheduleProposalService proposalService;
    private final ProposalConfirmationService confirmations;

    public ChatApplicationService(ConversationStore conversations, ChatAssistant assistant,
                                  ScheduleProposalService proposalService,
                                  ProposalConfirmationService confirmations) {
        this.conversations = conversations;
        this.assistant = assistant;
        this.proposalService = proposalService;
        this.confirmations = confirmations;
    }

    public Conversation createConversation(String ownerId) {
        return conversations.save(Conversation.create(ownerId));
    }

    public List<Conversation> listConversations(String ownerId) {
        return conversations.findRecentByOwnerId(ownerId, 50);
    }

    public ProposalConfirmationService.ConfirmationResponse confirmProposal(UUID proposalId, String ownerId) {
        ProposalConfirmationService.ConfirmationResponse confirmation = confirmations.confirm(proposalId, ownerId);
        String reply = confirmation.alreadyConfirmed()
                ? "This email was already scheduled. You can see it in your scheduled list."
                : "Confirmed. Your email has been scheduled. You can see it in your scheduled list.";
        appendConfirmationToConversation(confirmation.conversationId(), ownerId, reply);
        return confirmation;
    }

    public Conversation requireConversation(UUID id, String ownerId) {
        return conversations.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    public Flux<ChatEvent> send(UUID conversationId, String ownerId, String userText, ZoneId timezone) {
        boolean verbalConfirmation = isVerbalConfirmation(userText);
        if (!assistant.isConfigured() && !verbalConfirmation) {
            log.warn("Chat request rejected because AI provider is not configured; conversationId={}", conversationId);
            return Flux.error(new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI service is not configured. Check the selected provider and its credentials."));
        }

        Conversation conversation = requireConversation(conversationId, ownerId);
        log.info("Chat request accepted; conversationId={}, messageLength={}, timezone={}, historyMessages={}",
                conversationId, userText.length(), timezone, conversation.messages().size());
        Conversation withUser = conversation.append(new ChatMessage(UUID.randomUUID(), ChatMessage.Role.USER,
                userText, Instant.now()));
        conversations.save(withUser);

        if (verbalConfirmation) {
            return Mono.fromCallable(() -> confirmFromChat(conversationId, ownerId, withUser))
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume(error -> {
                        log.error("Verbal proposal confirmation failed; conversationId={}, exceptionType={}",
                                conversationId, error.getClass().getSimpleName(), error);
                        return Mono.just(new ChatEvent("error",
                                "I couldn't confirm this proposal. Please check your scheduled list before trying again.",
                                null, "PROPOSAL_CONFIRMATION_FAILED", false));
                    }).flux();
        }

        StringBuilder answer = new StringBuilder();
        String applicationState = confirmations.latestProposalContext(conversationId, ownerId);
        AtomicReference<String> stage = new AtomicReference<>("stream_reply");
        return assistant.streamReply(conversation.messages(), userText, timezone, applicationState)
                .map(chunk -> {
                    answer.append(chunk);
                    return new ChatEvent("text_delta", chunk, null);
                })
                .concatWith(Mono.defer(() -> {
                    stage.set("classify_reply");
                    return assistant.classify(conversation.messages(), userText, answer.toString(), timezone,
                                    applicationState)
                            .map(turn -> {
                                stage.set("persist_turn");
                                return finishTurn(conversationId, ownerId, withUser, answer, turn);
                            });
                }))
                .onErrorResume(error -> {
                    Throwable rootCause = rootCause(error);
                    String safeMessage = rootCause.getMessage() == null ? "(no message)"
                            : rootCause.getMessage().replaceAll("[\\r\\n\\t]", " ");
                    if (safeMessage.length() > 500) safeMessage = safeMessage.substring(0, 500);
                    AiFailure failure = classifyFailure(error, stage.get());
                    log.error("Chat request failed; conversationId={}, stage={}, exceptionType={}, rootCauseType={}, rootCauseMessage={}, errorCode={}, retryable={}",
                            conversationId, stage.get(), error.getClass().getSimpleName(),
                            rootCause.getClass().getSimpleName(), safeMessage, failure.code(), failure.retryable());
                    return Flux.just(new ChatEvent("error", failure.message(), null, failure.code(), failure.retryable()));
                });
    }

    private ChatEvent confirmFromChat(UUID conversationId, String ownerId, Conversation withUser) {
        Optional<ProposalConfirmationService.ConfirmationResponse> confirmation =
                confirmations.confirmLatestReady(conversationId, ownerId);
        String reply;
        String eventKind;
        if (confirmation.isPresent()) {
            reply = confirmation.get().alreadyConfirmed()
                    ? "This email was already scheduled. You can see it in your scheduled list."
                    : "Confirmed. Your email has been scheduled. You can see it in your scheduled list.";
            eventKind = "confirmed";
        } else {
            reply = "I don't have a valid proposal ready to confirm. Please provide the schedule details again, "
                    + "then you can confirm it here or with the Confirm schedule button.";
            eventKind = "replace";
        }
        conversations.save(withUser.append(new ChatMessage(UUID.randomUUID(), ChatMessage.Role.ASSISTANT,
                reply, Instant.now())));
        log.info("Verbal chat confirmation completed; conversationId={}, confirmed={}",
                conversationId, confirmation.isPresent());
        return new ChatEvent(eventKind, reply, null);
    }

    private void appendConfirmationToConversation(UUID conversationId, String ownerId, String reply) {
        conversations.findByIdAndOwnerId(conversationId, ownerId).ifPresentOrElse(conversation ->
                        conversations.save(conversation.append(new ChatMessage(UUID.randomUUID(),
                                ChatMessage.Role.ASSISTANT, reply, Instant.now()))),
                () -> log.error("Could not persist proposal confirmation in chat history; conversationId={}",
                        conversationId));
    }

    private static boolean isVerbalConfirmation(String text) {
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
        return Set.of(
                "yes", "yes please", "yes looks good", "yes this works", "yes that works",
                "yes go ahead", "yes please go ahead", "yes looks good go ahead", "yes schedule it",
                "yes confirm", "yes confirm it", "sure", "sure go ahead", "sure schedule it",
                "go ahead", "please go ahead", "looks good", "looks good go ahead", "looks good schedule it",
                "this works", "that works", "approved", "i confirm", "please confirm", "confirm",
                "confirm schedule", "confirm it", "schedule it", "please schedule it", "proceed", "please proceed"
        ).contains(normalized);
    }

    private ChatEvent finishTurn(UUID conversationId, String ownerId, Conversation withUser,
                                 StringBuilder answer, ChatAssistant.AssistantTurn turn) {
        String kind = turn.kind() == null ? "CHAT" : turn.kind().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("CHAT", "CLARIFICATION", "PROPOSAL").contains(kind)) kind = "CHAT";
        ScheduleProposal proposal = null;
        if ("PROPOSAL".equals(kind)) {
            try {
                proposal = proposalService.create(conversationId, ownerId, turn.proposal());
                log.info("Schedule proposal created; conversationId={}, proposalId={}, fixedEmails={}, recurrence={}",
                        conversationId, proposal.id(), proposal.emails().size(), proposal.recurrence() != null);
            } catch (InvalidScheduleProposalException invalid) {
                log.warn("Schedule proposal rejected by validation; conversationId={}, reason={}",
                        conversationId, invalid.getMessage());
                kind = "CLARIFICATION";
                String correction = "I couldn't prepare the schedule. "
                        + invalid.getMessage() + " Please correct it and send the request again.";
                conversations.save(withUser.append(new ChatMessage(UUID.randomUUID(), ChatMessage.Role.ASSISTANT,
                        correction, Instant.now())));
                log.info("Chat turn completed; conversationId={}, resultKind={}, proposalCreated=false",
                        conversationId, kind);
                return new ChatEvent("replace", correction, null);
            }
        }
        conversations.save(withUser.append(new ChatMessage(UUID.randomUUID(), ChatMessage.Role.ASSISTANT,
                answer.toString(), Instant.now())));
        log.info("Chat turn completed; conversationId={}, resultKind={}, proposalCreated={}",
                conversationId, kind, proposal != null);
        return new ChatEvent(kind, "", proposal, null, false);
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }

    private static AiFailure classifyFailure(Throwable error, String stage) {
        AiProviderException providerFailure = findCause(error, AiProviderException.class);
        if (providerFailure != null) {
            return new AiFailure(providerFailure.code(), providerFailure.retryable(), providerFailure.userMessage());
        }
        if ("stream_reply".equals(stage) || "classify_reply".equals(stage)) {
            return new AiFailure("AI_REQUEST_FAILED", false,
                    "The assistant could not complete this request. No schedule was created; please try again.");
        }
        return new AiFailure("CHAT_PROCESSING_FAILED", false,
                "The assistant could not finish saving this conversation. Check your schedules before retrying.");
    }

    private static <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        for (Throwable current = error; current != null && current.getCause() != current; current = current.getCause()) {
            if (type.isInstance(current)) return type.cast(current);
        }
        return null;
    }

    private record AiFailure(String code, boolean retryable, String message) { }

    public record ChatEvent(String kind, String reply, ScheduleProposal proposal, String errorCode, boolean retryable) {
        public ChatEvent(String kind, String reply, ScheduleProposal proposal) {
            this(kind, reply, proposal, null, false);
        }
    }
}
