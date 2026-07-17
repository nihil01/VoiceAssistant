package com.nihil.voice.knowledge;

public record IndexedKnowledgeEntry(
        RemoteKnowledgeEntry source,
        String contentHash,
        float[] embedding,
        boolean preserveExistingEmbedding
) {
}