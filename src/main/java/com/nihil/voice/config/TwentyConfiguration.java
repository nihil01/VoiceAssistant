package com.nihil.voice.config;

import com.nihil.voice.crm.CrmSyncJobStore;
import com.nihil.voice.crm.CrmSyncService;
import com.nihil.voice.crm.CrmSyncWorker;
import com.nihil.voice.crm.R2dbcCrmSyncJobStore;
import com.nihil.voice.crm.TwentyCrmGateway;
import com.nihil.voice.crm.TwentyCrmProperties;
import com.nihil.voice.crm.WebClientTwentyCrmGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(TwentyCrmProperties.class)
@ConditionalOnProperty(
        prefix = "voice.twenty",
        name = "enabled",
        havingValue = "true"
)
public class TwentyConfiguration {
    @Bean
    TwentyCrmGateway twentyCrmGateway(
            WebClient.Builder builder,
            TwentyCrmProperties properties
    ) {
        return new WebClientTwentyCrmGateway(builder, properties);
    }

    @Bean
    CrmSyncService crmSyncService(TwentyCrmGateway gateway) {
        return new CrmSyncService(gateway);
    }

    @Bean
    CrmSyncJobStore crmSyncJobStore(DatabaseClient db) {
        return new R2dbcCrmSyncJobStore(db);
    }

    @Bean
    CrmSyncWorker crmSyncWorker(
            CrmSyncJobStore jobs,
            CrmSyncService sync,
            @Value("${voice.twenty.batch-size:10}") int batchSize
    ) {
        return new CrmSyncWorker(jobs, sync, batchSize);
    }

    @Bean
    CrmSyncScheduler crmSyncScheduler(CrmSyncWorker worker) {
        return new CrmSyncScheduler(worker);
    }

    static final class CrmSyncScheduler {
        private final CrmSyncWorker worker;

        CrmSyncScheduler(CrmSyncWorker worker) {
            this.worker = worker;
        }

        @Scheduled(fixedDelayString = "${voice.twenty.poll-delay:5s}")
        void run() {
            worker.scheduledRun();
        }
    }
}
