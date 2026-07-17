package com.nihil.voice.knowledge;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface KnowledgeRetriever {
    KnowledgeRetriever NOOP = query -> Mono.empty();

    Mono<String> retrieve(String query);
}