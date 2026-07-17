package com.nihil.voice.knowledge;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface EmbeddingClient {
    Mono<float[]> embed(String text);
}