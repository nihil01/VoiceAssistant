package com.nihil.voice.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class KnowledgeBaseSynchronizerTest {
    @Test
    void embedsChangedActiveEntriesAndReplacesTheTwentySnapshot() {
        var entries = List.of(
                new RemoteKnowledgeEntry("kb-1", "Доставка", "Ежедневно", "FAQ", null, true, Instant.now()),
                new RemoteKnowledgeEntry("kb-2", "Архив", "Старое", null, null, false, Instant.now())
        );
        var store = new RecordingSyncStore();
        var embeddingCalls = new AtomicInteger();
        EmbeddingClient embeddings = text -> {
            embeddingCalls.incrementAndGet();
            return Mono.just(new float[]{0.1f, 0.2f});
        };
        var synchronizer = new KnowledgeBaseSynchronizer(
                () -> Flux.fromIterable(entries),
                embeddings,
                store
        );

        StepVerifier.create(synchronizer.synchronize()).verifyComplete();

        assertThat(embeddingCalls).hasValue(1);
        assertThat(store.snapshot).hasSize(2);
        assertThat(store.snapshot.getFirst().embedding()).containsExactly(0.1f, 0.2f);
        assertThat(store.snapshot.get(1).embedding()).isNull();
    }

    @Test
    void doesNotReembedUnchangedEntry() {
        var entry = new RemoteKnowledgeEntry("kb-1", "Доставка", "Ежедневно", "FAQ", null, true, Instant.now());
        var store = new RecordingSyncStore();
        store.hashes.put("kb-1", KnowledgeBaseSynchronizer.contentHash(entry));
        var embeddingCalls = new AtomicInteger();
        var synchronizer = new KnowledgeBaseSynchronizer(
                () -> Flux.just(entry),
                text -> {
                    embeddingCalls.incrementAndGet();
                    return Mono.just(new float[]{1});
                },
                store
        );

        StepVerifier.create(synchronizer.synchronize()).verifyComplete();

        assertThat(embeddingCalls).hasValue(0);
        assertThat(store.snapshot.getFirst().embedding()).isNull();
    }

    private static final class RecordingSyncStore implements KnowledgeSyncStore {
        private final Map<String, String> hashes = new ConcurrentHashMap<>();
        private List<IndexedKnowledgeEntry> snapshot = new ArrayList<>();

        @Override
        public Mono<Boolean> hasContentHash(String remoteId, String hash) {
            return Mono.just(hash.equals(hashes.get(remoteId)));
        }

        @Override
        public Mono<Void> replaceTwentySnapshot(List<IndexedKnowledgeEntry> entries) {
            snapshot = List.copyOf(entries);
            return Mono.empty();
        }

        @Override
        public Flux<KnowledgeSnippet> searchByVector(float[] embedding, int limit) {
            return Flux.empty();
        }

        @Override
        public Flux<KnowledgeSnippet> searchByText(String query, int limit) {
            return Flux.empty();
        }
    }
}
