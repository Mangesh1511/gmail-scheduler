package com.example.scheduler.chat.api;

import com.example.scheduler.auth.CurrentUser;
import com.example.scheduler.chat.application.ChatApplicationService;
import com.example.scheduler.chat.application.ChatApplicationService.ChatEvent;
import com.example.scheduler.chat.application.ProposalConfirmationService;
import com.example.scheduler.chat.api.ChatDtos.ConversationSummary;
import com.example.scheduler.chat.api.ChatDtos.ConversationView;
import com.example.scheduler.chat.api.ChatDtos.ProposalView;
import com.example.scheduler.chat.api.ChatDtos.SendMessageRequest;
import com.example.scheduler.chat.domain.Conversation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatApplicationService chat;
    private final CurrentUser currentUser;

    public ChatController(ChatApplicationService chat, CurrentUser currentUser) {
        this.chat = chat;
        this.currentUser = currentUser;
    }

    @PostMapping("/conversations")
    public ConversationSummary create(HttpServletRequest request) {
        return ConversationSummary.from(chat.createConversation(currentUser.requireOwnerId(request)));
    }

    @GetMapping("/conversations")
    public List<ConversationSummary> list(HttpServletRequest request) {
        return chat.listConversations(currentUser.requireOwnerId(request)).stream().map(ConversationSummary::from).toList();
    }

    @GetMapping("/conversations/{id}")
    public ConversationView get(@PathVariable UUID id, HttpServletRequest request) {
        return ConversationView.from(chat.requireConversation(id, currentUser.requireOwnerId(request)));
    }

    @PostMapping(value = "/conversations/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> send(@PathVariable UUID id, @Valid @RequestBody SendMessageRequest body,
                                               HttpServletRequest request) {
        ZoneId timezone = body.timezone() == null ? ZoneId.of("UTC") : body.timezone();
        return chat.send(id, currentUser.requireOwnerId(request), body.message(), timezone)
                .map(event -> ServerSentEvent.builder((Object) toPayload(event))
                        .event(event.kind().toLowerCase(java.util.Locale.ROOT)).build());
    }

    @PostMapping("/proposals/{id}/confirm")
    public ProposalConfirmationService.ConfirmationResponse confirm(@PathVariable UUID id, HttpServletRequest request) {
        return chat.confirmProposal(id, currentUser.requireOwnerId(request));
    }

    private static Object toPayload(ChatEvent event) {
        if ("error".equals(event.kind())) {
            return Map.of("reply", event.reply(), "errorCode", event.errorCode(), "retryable", event.retryable());
        }
        if (event.proposal() != null) {
            return Map.of("reply", event.reply(), "proposal", ProposalView.from(event.proposal()));
        }
        return Map.of("reply", event.reply());
    }
}
