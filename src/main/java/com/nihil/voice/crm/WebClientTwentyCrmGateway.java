package com.nihil.voice.crm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public final class WebClientTwentyCrmGateway implements TwentyCrmGateway {
    private static final String EXTERNAL_CALL_FILTER = "externalCallId[eq]:";

    private final WebClient web;
    private final TwentyCrmProperties properties;

    public WebClientTwentyCrmGateway(
            WebClient.Builder builder,
            TwentyCrmProperties properties
    ) {
        this.properties = properties;
        this.web = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + properties.apiKey()
                )
                .build();
    }

    @Override
    public Mono<String> findPersonIdByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return Mono.empty();
        }
        return findId(
                properties.peoplePath(),
                "phones.primaryPhoneNumber[eq]:" + phone
        );
    }

    @Override
    public Mono<String> createPerson(String phone, String displayName) {
        String safeName = displayName == null || displayName.isBlank()
                ? "Unknown caller"
                : displayName.strip();

        Map<String, Object> body = Map.of(
                "name", Map.of("firstName", safeName, "lastName", ""),
                "phones", Map.of(
                        "primaryPhoneNumber", Objects.requireNonNullElse(phone, ""),
                        "primaryPhoneCountryCode", "",
                        "additionalPhones", List.of()
                )
        );
        return create(properties.peoplePath(), body);
    }

    @Override
    public Mono<String> upsertAiCall(CrmCallData call, String personId) {
        var body = new LinkedHashMap<String, Object>();
        body.put("name", "Call " + call.externalCallId());
        body.put("externalCallId", call.externalCallId());
        body.put("callerNumber", call.callerNumber());
        body.put("destinationNumber", call.destinationNumber());
        body.put("startedAt", call.startedAt());
        body.put("endedAt", call.endedAt());
        body.put("durationSeconds", call.durationSeconds());
        body.put("status", call.status());
        body.put("shortSummary", call.summary().shortSummary());
        body.put("fullSummary", call.summary().fullSummary());
        body.put("intent", call.summary().intent());
        body.put("sentiment", call.summary().sentiment());
        body.put("outcome", call.summary().outcome());
        body.put("transcript", call.transcript());
        body.put("recordingUrl", call.recordingUrl());
        body.put("personId", personId);
        body.values().removeIf(Objects::isNull);

        return upsert(
                properties.aiCallsPath(),
                EXTERNAL_CALL_FILTER + call.externalCallId(),
                body
        );
    }

    @Override
    public Mono<String> upsertCallRecording(CrmCallData call, String aiCallId) {
        var body = new LinkedHashMap<String, Object>();
        body.put("name", "Recording " + call.externalCallId());
        body.put("externalCallId", call.externalCallId());
        body.put("recordingUrl", call.recordingUrl());
        body.put("durationSeconds", call.durationSeconds());
        body.put("status", "AVAILABLE");
        body.put("aiCallId", aiCallId);
        body.values().removeIf(Objects::isNull);

        return upsert(
                properties.callRecordingsPath(),
                EXTERNAL_CALL_FILTER + call.externalCallId(),
                body
        );
    }

    @Override
    public Mono<Void> createNote(String personId, String aiCallId, String body) {
        if (body == null || body.isBlank()) {
            return Mono.empty();
        }
        return create(
                properties.notesPath(),
                Map.of(
                        "title", "AI call summary",
                        "bodyV2", Map.of("markdown", body)
                )
        ).flatMap(noteId -> createIgnoringId(
                properties.noteTargetsPath(),
                Map.of(
                        "noteId", noteId,
                        "targetPersonId", personId
                )
        ));
    }

    @Override
    public Mono<Void> createTask(
            String personId,
            String aiCallId,
            String title
    ) {
        return create(
                properties.tasksPath(),
                Map.of(
                        "title", title,
                        "status", "TODO"
                )
        ).flatMap(taskId -> createIgnoringId(
                properties.taskTargetsPath(),
                Map.of(
                        "taskId", taskId,
                        "targetPersonId", personId
                )
        ));
    }

    private Mono<String> upsert(
            String path,
            String filter,
            Map<String, Object> body
    ) {
        return findId(path, filter)
                .flatMap(id -> update(path, id, body).thenReturn(id))
                .switchIfEmpty(Mono.defer(() -> create(path, body)));
    }

    private Mono<String> findId(String path, String filter) {
        return web.get()
                .uri(builder -> builder
                        .path(path)
                        .queryParam("filter", filter)
                        .queryParam("limit", 1)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(response -> extractId(response)
                        .map(Mono::just)
                        .orElseGet(Mono::empty));
    }

    private Mono<Void> update(
            String path,
            String id,
            Map<String, Object> body
    ) {
        return web.patch()
                .uri(path + "/{id}", id)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    private Mono<String> create(String path, Object body) {
        return web.post()
                .uri(path)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(response -> extractId(response)
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new IllegalStateException(
                                "Twenty response has no record id for " + path
                        ))));
    }

    private Mono<Void> createIgnoringId(String path, Object body) {
        return web.post()
                .uri(path)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    private Optional<String> extractId(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object id = map.get("id");
            if (id instanceof String text && !text.isBlank()) {
                return Optional.of(text);
            }
            for (Object nested : map.values()) {
                Optional<String> found = extractId(nested);
                if (found.isPresent()) {
                    return found;
                }
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object nested : iterable) {
                Optional<String> found = extractId(nested);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }
}
