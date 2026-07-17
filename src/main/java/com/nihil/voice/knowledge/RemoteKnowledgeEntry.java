package com.nihil.voice.knowledge;

import java.time.Instant;

public record RemoteKnowledgeEntry(
        String remoteId,
        String title,
        String content,
        String category,
        String sourceUrl,
        boolean active,
        Instant updatedAt
) {
}