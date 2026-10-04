package com.example.scheduler.chat.ai.bedrock;

import com.example.scheduler.chat.application.AiProviderException;
import com.example.scheduler.chat.application.LlmClient;
import com.example.scheduler.chat.application.ModelMessage;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bedrock Converse adapter using Bedrock API-key bearer authentication over HTTP. */
@Service
public class BedrockBearerLlmClient implements LlmClient {
    private final RestClient restClient;
    private final String provider;
    private final String bearerToken;
    private final String converseUri;

    public BedrockBearerLlmClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.ai.provider:none}") String provider,
            @Value("${app.ai.bedrock.region:us-east-1}") String region,
            @Value("${app.ai.bedrock.model:us.anthropic.claude-sonnet-4-6}") String model,
            @Value("${AWS_BEARER_TOKEN_BEDROCK:}") String bearerToken) {
        if (!region.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("Invalid Bedrock region configuration");
        }
        if (!model.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("Invalid Bedrock model configuration");
        }
        this.restClient = restClientBuilder.baseUrl("https://bedrock-runtime." + region + ".amazonaws.com").build();
        this.provider = provider;
        this.bearerToken = bearerToken;
        this.converseUri = "/model/" + model + "/converse";
    }

    @Override
    public boolean isConfigured() {
        return "bedrock-converse".equalsIgnoreCase(provider) && !bearerToken.isBlank();
    }

    @Override
    public Flux<String> stream(List<ModelMessage> messages) {
        if (!isConfigured()) return Flux.error(notConfigured());
        // Converse returns a completed message. The chat endpoint streams this result as one SSE content item.
        return Mono.fromCallable(() -> invoke(messages))
                .subscribeOn(Schedulers.boundedElastic())
                .flux();
    }

    @Override
    public Mono<String> generate(List<ModelMessage> messages) {
        if (!isConfigured()) return Mono.error(notConfigured());
        return Mono.fromCallable(() -> invoke(messages)).subscribeOn(Schedulers.boundedElastic());
    }

    private String invoke(List<ModelMessage> messages) {
        Map<String, Object> request = new LinkedHashMap<>();
        List<Map<String, String>> systemMessages = new ArrayList<>();
        List<Map<String, Object>> conversation = new ArrayList<>();
        for (ModelMessage message : messages) {
            if (message.role() == ModelMessage.Role.SYSTEM) {
                systemMessages.add(Map.of("text", message.content()));
            } else {
                conversation.add(Map.of(
                        "role", message.role() == ModelMessage.Role.USER ? "user" : "assistant",
                        "content", List.of(Map.of("text", message.content()))));
            }
        }
        if (!systemMessages.isEmpty()) request.put("system", systemMessages);
        request.put("messages", conversation);
        request.put("inferenceConfig", Map.of("maxTokens", 2048));

        try {
            JsonNode response = restClient.post()
                    .uri(converseUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + bearerToken)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode content = response == null ? null : response.path("output").path("message").path("content");
            if (content == null || !content.isArray()) {
                throw new AiProviderException("AI_RESPONSE_INVALID", false,
                        "Amazon Bedrock returned an empty response. Please try again.", null);
            }
            StringBuilder text = new StringBuilder();
            content.forEach(part -> {
                if (part.hasNonNull("text")) text.append(part.get("text").asText());
            });
            if (text.isEmpty()) {
                throw new AiProviderException("AI_RESPONSE_INVALID", false,
                        "Amazon Bedrock returned an empty response. Please try again.", null);
            }
            return text.toString();
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new AiProviderException("AI_PROVIDER_AUTHENTICATION_ERROR", false,
                        "Amazon Bedrock rejected the bearer token or model access. Check the token, region, and model access.", exception);
            }
            if (status == 408 || status == 429 || status >= 500) {
                throw new AiProviderException("AI_PROVIDER_TEMPORARILY_UNAVAILABLE", true,
                        "Amazon Bedrock is temporarily busy or unavailable. Your request was not completed; please try again shortly.", exception);
            }
            throw new AiProviderException("AI_PROVIDER_REQUEST_REJECTED", false,
                    "Amazon Bedrock rejected this request. Check the configured model and request settings.", exception);
        } catch (ResourceAccessException exception) {
            throw new AiProviderException("AI_PROVIDER_TEMPORARILY_UNAVAILABLE", true,
                    "Amazon Bedrock could not be reached. Your request was not completed; please try again shortly.", exception);
        }
    }

    private static AiProviderException notConfigured() {
        return new AiProviderException("AI_PROVIDER_NOT_CONFIGURED", false,
                "The AI service is not configured. Check the provider credentials and settings.", null);
    }
}
