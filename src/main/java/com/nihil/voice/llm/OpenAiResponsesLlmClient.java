package com.nihil.voice.llm;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

public final class OpenAiResponsesLlmClient implements LlmClient {

    private final WebClient web;
    private final String model;
    private final Duration timeout;
    private final String systemPrompt;
    private final OpenAiLlmEventParser parser;

    public OpenAiResponsesLlmClient(
            WebClient.Builder builder,
            ObjectMapper mapper,
            String baseUrl,
            String apiKey,
            String model,
            Duration timeout,
            String systemPrompt
    ) {
        this.web = builder
                .baseUrl(baseUrl)
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + apiKey
                )
                .build();

        this.model = model;
        this.timeout = timeout;
        this.systemPrompt = systemPrompt;
        this.parser = new OpenAiLlmEventParser(mapper);
    }

    @Override
    public Flux<String> stream(List<ConversationMessage> messages) {
        List<Map<String, String>> input = messages.stream()
                .map(message -> Map.of(
                        "role", message.role().name().toLowerCase(),
                        "content", message.content()
                ))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", input);
        body.put("stream", true);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("instructions", systemPrompt);
        }

        return web.post()
                .uri("/v1/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMapIterable(raw ->
                        parser.textDelta(raw).stream().toList()
                )
                .timeout(timeout);
    }
}