package com.nihil.voice.asterisk;

import com.nihil.voice.config.AsteriskProperties;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

public final class WebClientAriGateway implements AriGateway {
    private final WebClient client;
    private final AsteriskProperties properties;

    public WebClientAriGateway(WebClient.Builder builder, AsteriskProperties properties) {
        this.properties = properties;
        this.client = builder.baseUrl(properties.baseUrl().toString() + "/ari")
            .defaultHeaders(headers -> headers.setBasicAuth(properties.username(), properties.password() == null ? "" : properties.password()))
            .build();
    }

    public Mono<Void> answer(String channelId) { return post("/channels/{id}/answer", channelId); }

    public Mono<String> createBridge(String bridgeId, String name) {
        return client.post().uri(uri -> uri.path("/bridges").queryParam("type", "mixing")
                .queryParam("bridgeId", bridgeId).queryParam("name", name).build())
            .retrieve().bodyToMono(JsonNode.class).map(json -> json.path("id").asText(bridgeId)).defaultIfEmpty(bridgeId);
    }

    public Mono<Void> addChannel(String bridgeId, String channelId) {
        return client.post().uri(uri -> uri.path("/bridges/{id}/addChannel").queryParam("channel", channelId).build(bridgeId))
            .retrieve().bodyToMono(Void.class);
    }

    public Mono<AriChannel> createExternalMedia(String channelId, String callId) {
        return client.post().uri(uri -> uri.path("/channels/externalMedia")
                .queryParam("app", properties.app()).queryParam("external_host", "INCOMING")
                .queryParam("connection_type", "server").queryParam("transport", "websocket")
                .queryParam("encapsulation", "none").queryParam("format", properties.mediaFormat())
                .queryParam("direction", properties.mediaDirection()).queryParam("channelId", channelId)
                .queryParam("variables", "{variables}")
                .build(Map.of("variables", "{\"AI_CALL_ID\":\"" + callId + "\"}")))
            .accept(MediaType.APPLICATION_JSON).retrieve().bodyToMono(JsonNode.class)
            .map(json -> new AriChannel(json.path("id").asText(), json.path("name").asText(), null, null));
    }

    public Mono<String> channelVariable(String channelId, String variable) {
        return client.get().uri(uri -> uri.path("/channels/{id}/variable").queryParam("variable", variable).build(channelId))
            .retrieve().bodyToMono(JsonNode.class).map(json -> json.path("value").asText())
            .filter(value -> !value.isBlank()).switchIfEmpty(Mono.error(new AriException("Missing channel variable " + variable)));
    }

    public Mono<Void> deleteChannel(String channelId) { return delete("/channels/{id}", channelId); }
    public Mono<Void> deleteBridge(String bridgeId) { return delete("/bridges/{id}", bridgeId); }

    private Mono<Void> post(String path, String id) {
        return client.post().uri(path, Map.of("id", id)).retrieve().bodyToMono(Void.class);
    }
    private Mono<Void> delete(String path, String id) {
        return client.delete().uri(path, Map.of("id", id)).retrieve()
            .onStatus(status -> status.value() == 404, response -> Mono.empty()).bodyToMono(Void.class);
    }
}
