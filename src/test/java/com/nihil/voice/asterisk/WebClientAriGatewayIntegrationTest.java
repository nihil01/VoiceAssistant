package com.nihil.voice.asterisk;

import static org.assertj.core.api.Assertions.assertThat;

import com.nihil.voice.config.AsteriskProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

class WebClientAriGatewayIntegrationTest {
    @Test
    void sendsAuthenticatedServerModeBidirectionalExternalMediaRequest() {
        AtomicReference<String> uri = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        DisposableServer server = HttpServer.create().host("127.0.0.1").port(0)
            .route(routes -> routes.post("/ari/channels/externalMedia", (request, response) -> {
                uri.set(request.uri());
                authorization.set(request.requestHeaders().get("Authorization"));
                return response.header("content-type", "application/json")
                    .sendString(reactor.core.publisher.Mono.just("{\"id\":\"media-1\",\"name\":\"WebSocket/media-1\"}"));
            })).bindNow();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.port());
            var properties = new AsteriskProperties(true, base, "ari", "secret", "agent", "slin16", "both",
                URI.create("ws://127.0.0.1:" + server.port() + "/media"), Duration.ofSeconds(2), Duration.ofSeconds(3), 8, 8);
            var gateway = new WebClientAriGateway(WebClient.builder(), properties);

            StepVerifier.create(gateway.createExternalMedia("media-1", "call-1"))
                .assertNext(channel -> assertThat(channel.id()).isEqualTo("media-1"))
                .verifyComplete();

            assertThat(uri.get()).contains("external_host=INCOMING", "connection_type=server", "transport=websocket",
                "encapsulation=none", "format=slin16", "direction=both", "channelId=media-1");
            assertThat(authorization.get()).isEqualTo("Basic " + java.util.Base64.getEncoder()
                .encodeToString("ari:secret".getBytes(StandardCharsets.UTF_8)));
        } finally {
            server.disposeNow();
        }
    }
}
