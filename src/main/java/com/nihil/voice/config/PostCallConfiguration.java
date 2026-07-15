package com.nihil.voice.config;

import com.nihil.voice.llm.LlmClient;
import com.nihil.voice.postcall.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class PostCallConfiguration {
    @Bean CallSummaryProvider callSummaryProvider(ObjectProvider<LlmClient> llm, ObjectMapper mapper) {
        LlmClient client = llm.getIfAvailable();
        return client == null ? new HeuristicCallSummaryProvider() : new LlmCallSummaryProvider(client, mapper);
    }
    @Bean PostCallStore postCallStore(DatabaseClient db, TransactionalOperator tx, ObjectMapper mapper) {
        return new R2dbcPostCallStore(db, tx, mapper);
    }
    @Bean PostCallProcessor postCallProcessor(PostCallStore store, CallSummaryProvider provider) {
        return new PostCallProcessor(store, provider);
    }
    @Bean PostCallScheduler postCallScheduler(PostCallProcessor processor) {
        return new PostCallScheduler(processor);
    }
}
