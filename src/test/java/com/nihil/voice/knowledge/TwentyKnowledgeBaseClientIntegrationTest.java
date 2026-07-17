package com.nihil.voice.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

class TwentyKnowledgeBaseClientIntegrationTest {
    @Test
    void readsKnowledgeEntriesFromTwentyGeneratedRestEndpoint() {
        AtomicReference<String> authorization = new AtomicReference<>();
        DisposableServer server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.get("/rest/knowledgeBaseEntries", (request, response) -> {
                    authorization.set(request.requestHeaders().get("Authorization"));
                    return response.header("Content-Type", "application/json")
                            .sendString(Mono.just("""
                                    {"data":{"knowledgeBaseEntries":[
                                      {"id":"kb-1","title":"Доставка","content":"Ежедневно","category":"FAQ","sourceUrl":null,"active":true,"updatedAt":"2026-07-17T10:00:00Z"},
                                      {"id":"kb-2","title":"Скрыто","content":"Не использовать","active":false,"updatedAt":"2026-07-17T10:01:00Z"}
                                    ]}}
                                    """))
                            .then();
                }))
                .bindNow();
        try {
            var client = new TwentyKnowledgeBaseClient(
                    WebClient.builder(),
                    "http://127.0.0.1:" + server.port(),
                    "test-key",
                    "/rest/knowledgeBaseEntries"
            );

            StepVerifier.create(client.listEntries())
                    .assertNext(entry -> {
                        assertThat(entry.remoteId()).isEqualTo("kb-1");
                        assertThat(entry.title()).isEqualTo("Доставка");
                        assertThat(entry.active()).isTrue();
                    })
                    .assertNext(entry -> assertThat(entry.active()).isFalse())
                    .verifyComplete();

            assertThat(authorization.get()).isEqualTo("Bearer test-key");
        } finally {
            server.disposeNow();
        }
    }
}
