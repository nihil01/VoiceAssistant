package com.nihil.voice.asterisk;

import static org.assertj.core.api.Assertions.assertThat;

import com.nihil.voice.call.CallSessionManager;
import com.nihil.voice.config.AsteriskProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

class ReactorNettyMediaGatewayIntegrationTest {
    @Test
    void receivesBinaryAudioFromLocalAsteriskWebSocket() {
        DisposableServer server = HttpServer.create().host("127.0.0.1").port(0)
            .route(routes -> routes.ws("/media/connection-1", (in, out) ->
                out.sendByteArray(Mono.just(new byte[]{4, 5, 6})).then(Mono.never())))
            .bindNow();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.port());
            var properties = new AsteriskProperties(true, base, "ari", "secret", "agent", "slin16", "both",
                URI.create("ws://127.0.0.1:" + server.port() + "/media"), Duration.ofSeconds(2), Duration.ofSeconds(2), 8, 8);
            var calls = new CallSessionManager();
            var call = calls.create("caller-1", "123", "700");
            calls.bindMedia(call.internalCallId(), "media-1", "connection-1");
            var gateway = new ReactorNettyMediaGateway(properties, new SimpleMeterRegistry());

            StepVerifier.create(gateway.connect(call).flatMapMany(connection ->
                    connection.inboundAudio().take(1)
                        .concatWith(connection.close().thenMany(reactor.core.publisher.Flux.empty()))))
                .assertNext(bytes -> assertThat(bytes).containsExactly(4, 5, 6))
                .verifyComplete();
        } finally {
            server.disposeNow();
        }
    }
}
