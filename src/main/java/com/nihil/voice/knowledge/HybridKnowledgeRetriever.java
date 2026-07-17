package com.nihil.voice.knowledge;

import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class HybridKnowledgeRetriever implements KnowledgeRetriever {
    private final EmbeddingClient embeddings;
    private final KnowledgeStore store;
    private final int limit;

    public HybridKnowledgeRetriever(EmbeddingClient embeddings, KnowledgeStore store, int limit) {
        this.embeddings = embeddings;
        this.store = store;
        this.limit = Math.max(1, limit);
    }

    @Override
    public Mono<String> retrieve(String query) {
        if (query == null || query.isBlank()) {
            return Mono.empty();
        }
        Flux<KnowledgeSnippet> results = embeddings.embed(query.strip())
                .flatMapMany(vector -> store.searchByVector(vector, limit))
                .onErrorResume(ignored -> store.searchByText(query.strip(), limit))
                .switchIfEmpty(Flux.defer(() -> store.searchByText(query.strip(), limit)));
        return results.collectList()
                .filter(entries -> !entries.isEmpty())
                .map(HybridKnowledgeRetriever::format);
    }

    private static String format(List<KnowledgeSnippet> entries) {
        var context = new StringBuilder();
        for (KnowledgeSnippet entry : entries) {
            if (!context.isEmpty()) {
                context.append("\n\n");
            }
            if (entry.category() != null && !entry.category().isBlank()) {
                context.append('[').append(entry.category().strip()).append("] ");
            }
            context.append(entry.title().strip()).append("\n");
            context.append(entry.content().strip());
        }
        return context.toString();
    }
}