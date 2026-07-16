package com.nihil.voice.asterisk;

import com.nihil.voice.config.AsteriskProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

/** Cold, reconnecting ARI event stream. Authorization is sent as a header and never logged. */
public final class AriEventStream {
    private static final Logger log = LoggerFactory.getLogger(AriEventStream.class);
    private final WebSocketClient client;
    private final AsteriskProperties properties;
    private final AriEventParser parser;

    public AriEventStream(AsteriskProperties properties, AriEventParser parser) {
        this(new ReactorNettyWebSocketClient(), properties, parser);
    }
    AriEventStream(WebSocketClient client, AsteriskProperties properties, AriEventParser parser) {
        this.client = client; this.properties = properties; this.parser = parser;
    }

    public Flux<AriEvent> events() {
        return Flux.defer(() -> {
            log.info("Connecting ARI event WebSocket host={} app={}",
                properties.baseUrl().getAuthority(), properties.app());
            return Flux.<AriEvent>create(emitter -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(properties.username(), properties.password() == null ? "" : properties.password());
            Disposable connection = client.execute(properties.eventsUri(), headers, session ->
                session.receive()
                    .doOnSubscribe(ignored -> log.info("ARI event WebSocket connected host={} app={}",
                        properties.baseUrl().getAuthority(), properties.app()))
                    .filter(message -> message.getType() == WebSocketMessage.Type.TEXT)
                    .map(WebSocketMessage::getPayloadAsText)
                    .doOnNext(raw -> parser.parse(raw).ifPresent(emitter::next))
                    .then()
            ).subscribe(
                ignored -> { },
                emitter::error,
                () -> emitter.error(new AriException("ARI event WebSocket closed"))
            );
            emitter.onDispose(connection);
            });
        }).doOnError(error -> log.warn("ARI event WebSocket failed host={} app={} error={}",
                properties.baseUrl().getAuthority(), properties.app(), error.toString()))
          .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
              .maxBackoff(Duration.ofSeconds(30))
              .doBeforeRetry(signal -> log.warn("Retrying ARI event WebSocket attempt={} error={}",
                  signal.totalRetries() + 1, signal.failure().toString())));
    }
}
