package com.nihil.voice.knowledge;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

public final class OpenAiEmbeddingClient implements EmbeddingClient {
    private final WebClient web;
    private final String model;
    private final int dimensions;
    private final Duration timeout;

    public OpenAiEmbeddingClient(
            WebClient.Builder builder,
            ObjectMapper ignoredMapper,
            String baseUrl,
            String apiKey,
            String model,
            int dimensions,
            Duration timeout
    ) {
        this.web = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.model = model;
        this.dimensions = dimensions;
        this.timeout = timeout;
    }

    @Override
    public Mono<float[]> embed(String text) {
        if (text == null || text.isBlank()) {
            return Mono.error(new IllegalArgumentException("Embedding text is required"));
        }
        return web.post()
                .uri("/v1/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "model", model,
                        "input", text.strip(),
                        "dimensions", dimensions
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::extractVector)
                .timeout(timeout);
    }

    private float[] extractVector(Map<?, ?> response) {
        Object dataValue = response.get("data");
        if (!(dataValue instanceof List<?> data) || data.isEmpty()) {
            throw new IllegalStateException("Embedding response has no data");
        }
        if (!(data.getFirst() instanceof Map<?, ?> item)) {
            throw new IllegalStateException("Embedding response item is invalid");
        }
        Object embeddingValue = item.get("embedding");
        if (!(embeddingValue instanceof List<?> values) || values.size() != dimensions) {
            throw new IllegalStateException("Embedding response has unexpected dimensions");
        }
        float[] vector = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            if (!(value instanceof Number number)) {
                throw new IllegalStateException("Embedding contains a non-numeric value");
            }
            vector[index] = number.floatValue();
        }
        return vector;
    }
}
