package com.nihil.voice.asterisk;

import static org.assertj.core.api.Assertions.assertThat;

import com.nihil.voice.config.AsteriskProperties;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

class AriEventStreamIntegrationTest {
    @Test
    void reconnectsWhenAsteriskClosesWebSocketNormally() {
        AtomicInteger connections = new AtomicInteger();
        String event = """
                {
                  "type":"StasisStart",
                  "channel":{
                    "id":"caller-1",
                    "name":"PJSIP/700-1",
                    "caller":{"number":"+9941"},
                    "dialplan":{"exten":"700"}
                  },
                  "args":[]
                }
                """;
        DisposableServer server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.ws("/ari/events", (in, out) -> {
                    connections.incrementAndGet();
                    return out.sendString(Mono.just(event)).then();
                }))
                .bindNow();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.port());
            var properties = new AsteriskProperties(
                    true,
                    base,
                    "ari",
                    "secret",
                    "ai-agent",
                    "slin16",
                    "both",
                    URI.create("ws://127.0.0.1:" + server.port() + "/media"),
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(3),
                    8,
                    8
            );
            var stream = new AriEventStream(
                    properties,
                    new AriEventParser(new ObjectMapper())
            );

            StepVerifier.create(stream.events().take(2))
                    .expectNextCount(2)
                    .verifyComplete();

            assertThat(connections.get()).isGreaterThanOrEqualTo(2);
        } finally {
            server.disposeNow();
        }
    }
}
