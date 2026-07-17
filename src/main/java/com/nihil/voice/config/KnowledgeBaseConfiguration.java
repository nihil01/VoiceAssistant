package com.nihil.voice.config;

import com.nihil.voice.crm.TwentyCrmProperties;
import com.nihil.voice.knowledge.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KnowledgeBaseProperties.class)
@ConditionalOnProperty(prefix = "voice.knowledge", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeBaseConfiguration {
    @Bean
    KnowledgeSyncStore knowledgeStore(DatabaseClient db, TransactionalOperator tx, KnowledgeBaseProperties properties) {
        return new R2dbcKnowledgeStore(db, tx, properties.tenantId(), properties.maximumDistance());
    }

    @Bean
    @ConditionalOnProperty(prefix = "voice.providers.openai", name = "enabled", havingValue = "true")
    EmbeddingClient embeddingClient(WebClient.Builder builder, ObjectMapper mapper, OpenAiProperties openAi, KnowledgeBaseProperties knowledge) {
        return new OpenAiEmbeddingClient(builder, mapper, openAi.baseUrl(), openAi.apiKey(),
                knowledge.embeddingModel(), knowledge.embeddingDimensions(), knowledge.embeddingTimeout());
    }

    @Bean
    @ConditionalOnBean(EmbeddingClient.class)
    KnowledgeRetriever knowledgeRetriever(EmbeddingClient embeddings, KnowledgeSyncStore store, KnowledgeBaseProperties properties) {
        return new HybridKnowledgeRetriever(embeddings, store, properties.maximumResults());
    }

    @Bean
    @ConditionalOnProperty(prefix = "voice.twenty", name = "enabled", havingValue = "true")
    TwentyKnowledgeBaseClient twentyKnowledgeBaseClient(WebClient.Builder builder, TwentyCrmProperties twenty) {
        return new TwentyKnowledgeBaseClient(builder, twenty.baseUrl(), twenty.apiKey(), "/rest/knowledgeBaseEntries");
    }

    @Bean
    @ConditionalOnBean({TwentyKnowledgeBaseClient.class, EmbeddingClient.class})
    KnowledgeBaseSynchronizer knowledgeBaseSynchronizer(TwentyKnowledgeBaseClient source, EmbeddingClient embeddings, KnowledgeSyncStore store) {
        return new KnowledgeBaseSynchronizer(source, embeddings, store);
    }

    @Bean
    @ConditionalOnBean(KnowledgeBaseSynchronizer.class)
    KnowledgeSyncTick knowledgeSyncTick(KnowledgeBaseSynchronizer synchronizer) {
        return new KnowledgeSyncTick(synchronizer);
    }

    static final class KnowledgeSyncTick {
        private static final Logger log = LoggerFactory.getLogger(KnowledgeSyncTick.class);
        private final KnowledgeBaseSynchronizer synchronizer;
        KnowledgeSyncTick(KnowledgeBaseSynchronizer synchronizer) { this.synchronizer = synchronizer; }

        @Scheduled(initialDelayString = "${voice.knowledge.sync-initial-delay:5s}", fixedDelayString = "${voice.knowledge.sync-delay:30s}")
        void synchronize() {
            synchronizer.synchronize()
                    .doOnSuccess(ignored -> log.debug("Twenty knowledge-base snapshot synchronized"))
                    .doOnError(error -> log.warn("Twenty knowledge-base synchronization failed: {}", error.getMessage()))
                    .subscribe();
        }
    }
}