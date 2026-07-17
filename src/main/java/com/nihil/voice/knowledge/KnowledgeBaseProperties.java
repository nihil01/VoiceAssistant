package com.nihil.voice.knowledge;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("voice.knowledge")
public record KnowledgeBaseProperties(boolean enabled, UUID tenantId, String embeddingModel,
                                      int embeddingDimensions, int maximumResults,
                                      double maximumDistance, Duration embeddingTimeout) {
    private static final UUID DEFAULT_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public KnowledgeBaseProperties {
        tenantId = tenantId == null ? DEFAULT_TENANT : tenantId;
        embeddingModel = embeddingModel == null || embeddingModel.isBlank() ? "text-embedding-3-small" : embeddingModel;
        embeddingDimensions = embeddingDimensions < 1 ? 1536 : embeddingDimensions;
        maximumResults = maximumResults < 1 ? 4 : maximumResults;
        maximumDistance = maximumDistance <= 0 ? 0.55 : maximumDistance;
        embeddingTimeout = embeddingTimeout == null ? Duration.ofSeconds(5) : embeddingTimeout;
    }
}