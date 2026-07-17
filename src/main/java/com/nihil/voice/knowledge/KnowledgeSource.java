package com.nihil.voice.knowledge;

import reactor.core.publisher.Flux;

@FunctionalInterface
public interface KnowledgeSource {
    Flux<RemoteKnowledgeEntry> listEntries();
}