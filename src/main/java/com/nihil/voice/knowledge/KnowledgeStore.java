package com.nihil.voice.knowledge;

import reactor.core.publisher.Flux;

public interface KnowledgeStore {
    Flux<KnowledgeSnippet> searchByVector(float[] embedding, int limit);

    Flux<KnowledgeSnippet> searchByText(String query, int limit);
}