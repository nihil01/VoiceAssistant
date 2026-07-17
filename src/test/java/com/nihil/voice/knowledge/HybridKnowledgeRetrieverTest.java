package com.nihil.voice.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class HybridKnowledgeRetrieverTest {
    @Test
    void returnsVectorRankedEntriesAsCompactContext() {
        EmbeddingClient embeddings = text -> Mono.just(new float[]{0.1f, 0.2f});
        KnowledgeStore store = new StubStore(
                List.of(
                        new KnowledgeSnippet("Доставка", "Ежедневно 09:00–18:00", "FAQ"),
                        new KnowledgeSnippet("Оплата", "Карта или наличные", "FAQ")
                ),
                List.of()
        );

        var retriever = new HybridKnowledgeRetriever(embeddings, store, 4);

        StepVerifier.create(retriever.retrieve("Когда доставка?"))
                .assertNext(context -> {
                    assertThat(context).contains("[FAQ] Доставка", "Ежедневно 09:00–18:00");
                    assertThat(context).contains("[FAQ] Оплата", "Карта или наличные");
                })
                .verifyComplete();
    }

    @Test
    void fallsBackToTextSearchWhenEmbeddingProviderFails() {
        EmbeddingClient embeddings = text -> Mono.error(new IllegalStateException("provider unavailable"));
        KnowledgeStore store = new StubStore(
                List.of(),
                List.of(new KnowledgeSnippet("Адрес", "Баку, Низами 10", null))
        );

        var retriever = new HybridKnowledgeRetriever(embeddings, store, 4);

        StepVerifier.create(retriever.retrieve("адрес"))
                .assertNext(context -> assertThat(context).contains("Адрес", "Баку, Низами 10"))
                .verifyComplete();
    }

    @Test
    void fallsBackToTextSearchWhenNoVectorPassesTheRelevanceThreshold() {
        EmbeddingClient embeddings = text -> Mono.just(new float[]{0.1f});
        KnowledgeStore store = new StubStore(
                List.of(),
                List.of(new KnowledgeSnippet("График", "Пн–Пт 09:00–18:00", "FAQ"))
        );

        var retriever = new HybridKnowledgeRetriever(embeddings, store, 4);

        StepVerifier.create(retriever.retrieve("график"))
                .assertNext(context -> assertThat(context).contains("График", "Пн–Пт"))
                .verifyComplete();
    }

    private record StubStore(
            List<KnowledgeSnippet> vectorResults,
            List<KnowledgeSnippet> textResults
    ) implements KnowledgeStore {
        @Override
        public Flux<KnowledgeSnippet> searchByVector(float[] embedding, int limit) {
            return Flux.fromIterable(vectorResults);
        }

        @Override
        public Flux<KnowledgeSnippet> searchByText(String query, int limit) {
            return Flux.fromIterable(textResults);
        }
    }
}
