package com.nihil.voice.knowledge;

import java.util.List;
import reactor.core.publisher.Mono;

public interface KnowledgeSyncStore extends KnowledgeStore {
    Mono<Boolean> hasContentHash(String remoteId, String hash);

    Mono<Void> replaceTwentySnapshot(List<IndexedKnowledgeEntry> entries);
}