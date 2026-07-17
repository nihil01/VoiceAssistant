package com.nihil.voice.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

class OpenAiEmbeddingClientIntegrationTest {
    @Test
    void sendsEmbeddingRequestAndParsesVector() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        DisposableServer server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.post("/v1/embeddings", (request, response) ->
                        request.receive().aggregate().asString()
                                .doOnNext(requestBody::set)
                                .then(response.header("Content-Type", "application/json")
                                        .sendString(Mono.just("{\"data\":[{\"embedding\":[0.1,0.2,0.3]}]}"))
                                        .then())))
                .bindNow();
        try {
            var client = new OpenAiEmbeddingClient(
                    WebClient.builder(),
                    new ObjectMapper(),
                    "http://127.0.0.1:" + server.port(),
                    "test-key",
                    "text-embedding-3-small",
                    3,
                    Duration.ofSeconds(2)
            );

            StepVerifier.create(client.embed("доставка"))
                    .assertNext(vector -> assertThat(vector).containsExactly(0.1f, 0.2f, 0.3f))
                    .verifyComplete();

            assertThat(requestBody.get())
                    .contains("text-embedding-3-small", "доставка", "\"dimensions\":3");
        } finally {
            server.disposeNow();
        }
    }
}
