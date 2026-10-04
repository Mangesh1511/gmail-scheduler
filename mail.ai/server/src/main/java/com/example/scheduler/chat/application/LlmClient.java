package com.example.scheduler.chat.application;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/** Application-owned port for text generation; provider SDKs stay behind its adapters. */
public interface LlmClient {
    boolean isConfigured();

    Flux<String> stream(List<ModelMessage> messages);

    Mono<String> generate(List<ModelMessage> messages);
}
