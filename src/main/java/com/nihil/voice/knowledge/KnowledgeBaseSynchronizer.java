package com.nihil.voice.knowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import reactor.core.publisher.Mono;

public final class KnowledgeBaseSynchronizer {
    private final KnowledgeSource source;
    private final EmbeddingClient embeddings;
    private final KnowledgeSyncStore store;

    public KnowledgeBaseSynchronizer(
            KnowledgeSource source,
            EmbeddingClient embeddings,
            KnowledgeSyncStore store
    ) {
        this.source = source;
        this.embeddings = embeddings;
        this.store = store;
    }

    public Mono<Void> synchronize() {
        return source.listEntries()
                .concatMap(this::index)
                .collectList()
                .flatMap(store::replaceTwentySnapshot);
    }

    private Mono<IndexedKnowledgeEntry> index(RemoteKnowledgeEntry entry) {
        String hash = contentHash(entry);
        if (!entry.active()) {
            return Mono.just(new IndexedKnowledgeEntry(entry, hash, null, false));
        }
        return store.hasContentHash(entry.remoteId(), hash)
                .flatMap(unchanged -> {
                    if (unchanged) {
                        return Mono.just(new IndexedKnowledgeEntry(entry, hash, null, true));
                    }
                    String text = entry.title() + "\n" + entry.content();
                    return embeddings.embed(text)
                            .map(vector -> new IndexedKnowledgeEntry(entry, hash, vector, false))
                            .onErrorReturn(new IndexedKnowledgeEntry(entry, hash, null, false));
                });
    }

    static String contentHash(RemoteKnowledgeEntry entry) {
        String value = String.join("\u0000",
                entry.title(),
                entry.content(),
                entry.category() == null ? "" : entry.category(),
                entry.sourceUrl() == null ? "" : entry.sourceUrl(),
                Boolean.toString(entry.active())
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}