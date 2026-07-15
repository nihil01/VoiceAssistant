package com.nihil.voice.crm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public final class WebClientTwentyCrmGateway implements TwentyCrmGateway {
    private final WebClient web; private final TwentyCrmProperties properties;
    public WebClientTwentyCrmGateway(WebClient.Builder builder, TwentyCrmProperties properties) {
        this.properties = properties;
        this.web = builder.baseUrl(properties.baseUrl()).defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey()).build();
    }

    @Override public Mono<String> findPersonIdByPhone(String phone) {
        if (phone == null || phone.isBlank()) return Mono.empty();
        return findId(properties.peoplePath(), "phones.primaryPhoneNumber[eq]:" + phone);
    }

    @Override public Mono<String> createPerson(String phone, String displayName) {
        Map<String,Object> name = Map.of("firstName", displayName == null || displayName.isBlank() ? "Unknown caller" : displayName, "lastName", "");
        Map<String,Object> phones = Map.of("primaryPhoneNumber", phone, "primaryPhoneCountryCode", "", "additionalPhones", List.of());
        return create(properties.peoplePath(), Map.of("name", name, "phones", phones));
    }

    @Override public Mono<String> upsertAiCall(CrmCallData call, String personId) {
        var body = new LinkedHashMap<String,Object>();
        body.put("externalCallId", call.externalCallId()); body.put("callerNumber", call.callerNumber());
        body.put("destinationNumber", call.destinationNumber()); body.put("startedAt", call.startedAt()); body.put("endedAt", call.endedAt());
        body.put("shortSummary", call.summary().shortSummary()); body.put("fullSummary", call.summary().fullSummary());
        body.put("intent", call.summary().intent()); body.put("sentiment", call.summary().sentiment());
        body.put("outcome", call.summary().outcome()); body.put("transcript", call.transcript()); body.put("personId", personId);
        body.values().removeIf(java.util.Objects::isNull);
        return findId(properties.aiCallsPath(), "externalCallId[eq]:" + call.externalCallId())
            .flatMap(id -> web.patch().uri(properties.aiCallsPath() + "/{id}", id).bodyValue(body).retrieve().bodyToMono(Map.class).thenReturn(id))
            .switchIfEmpty(create(properties.aiCallsPath(), body));
    }

    @Override public Mono<Void> createNote(String personId, String aiCallId, String body) {
        return createIgnoringId(properties.notesPath(), Map.of("title", "AI call summary", "bodyV2", Map.of("markdown", body), "personId", personId));
    }

    @Override public Mono<Void> createTask(String personId, String aiCallId, String title) {
        return createIgnoringId(properties.tasksPath(), Map.of("title", title, "status", "TODO", "personId", personId));
    }

    private Mono<String> findId(String path, String filter) {
        return web.get().uri(builder -> builder.path(path).queryParam("filter", filter).build()).retrieve().bodyToMono(Map.class)
            .flatMap(response -> extractId(response).map(Mono::just).orElseGet(Mono::empty));
    }
    private Mono<String> create(String path, Object body) {
        return web.post().uri(path).bodyValue(body).retrieve().bodyToMono(Map.class)
            .flatMap(response -> extractId(response).map(Mono::just).orElseGet(() -> Mono.error(new IllegalStateException("Twenty response has no record id"))));
    }
    private Mono<Void> createIgnoringId(String path, Object body) { return web.post().uri(path).bodyValue(body).retrieve().toBodilessEntity().then(); }

    private Optional<String> extractId(Object value) {
        if (value instanceof Map<?,?> map) {
            Object id = map.get("id"); if (id instanceof String text && !text.isBlank()) return Optional.of(text);
            Object data = map.get("data"); Optional<String> fromData = extractId(data); if (fromData.isPresent()) return fromData;
            for (Object nested : map.values()) { Optional<String> found = extractId(nested); if (found.isPresent()) return found; }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object nested : iterable) { Optional<String> found = extractId(nested); if (found.isPresent()) return found; }
        }
        return Optional.empty();
    }
}
