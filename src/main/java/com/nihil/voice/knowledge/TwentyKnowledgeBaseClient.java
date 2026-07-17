package com.nihil.voice.knowledge;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

public final class TwentyKnowledgeBaseClient implements KnowledgeSource {
    private final WebClient web;
    private final String path;

    public TwentyKnowledgeBaseClient(
            WebClient.Builder builder,
            String baseUrl,
            String apiKey,
            String path
    ) {
        this.web = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.path = path;
    }

    @Override
    public Flux<RemoteKnowledgeEntry> listEntries() {
        return web.get()
                .uri(builder -> builder.path(path).queryParam("limit", 200).build())
                .retrieve()
                .bodyToMono(Map.class)
                .flatMapMany(response -> Flux.fromIterable(extractRecords(response)))
                .map(TwentyKnowledgeBaseClient::toEntry);
    }

    private static List<?> extractRecords(Map<?, ?> response) {
        Object dataValue = response.get("data");
        if (dataValue instanceof Map<?, ?> data) {
            Object records = data.get("knowledgeBaseEntries");
            if (records instanceof List<?> list) {
                return list;
            }
        }
        Object records = response.get("knowledgeBaseEntries");
        return records instanceof List<?> list ? list : List.of();
    }

    private static RemoteKnowledgeEntry toEntry(Object value) {
        if (!(value instanceof Map<?, ?> record)) {
            throw new IllegalStateException("Twenty knowledge-base record is invalid");
        }
        String remoteId = requiredText(record, "id");
        String title = requiredText(record, "title");
        String content = requiredText(record, "content");
        Object activeValue = record.get("active");
        boolean active = !(activeValue instanceof Boolean flag) || flag;
        return new RemoteKnowledgeEntry(
                remoteId,
                title,
                content,
                text(record, "category"),
                text(record, "sourceUrl"),
                active,
                instant(record.get("updatedAt"))
        );
    }

    private static String requiredText(Map<?, ?> record, String key) {
        String value = text(record, key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Twenty knowledge-base field is required: " + key);
        }
        return value;
    }

    private static String text(Map<?, ?> record, String key) {
        Object value = record.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(String.valueOf(value));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}