package com.nihil.voice.config;

import com.nihil.voice.crm.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods=false)
@EnableScheduling
@EnableConfigurationProperties(TwentyCrmProperties.class)
@ConditionalOnProperty(prefix="voice.twenty",name="enabled",havingValue="true")
public class TwentyConfiguration {
    @Bean TwentyCrmGateway twentyCrmGateway(WebClient.Builder builder,TwentyCrmProperties p){return new WebClientTwentyCrmGateway(builder,p);}
    @Bean CrmSyncService crmSyncService(TwentyCrmGateway gateway){return new CrmSyncService(gateway);}
    @Bean CrmSyncJobStore crmSyncJobStore(DatabaseClient db){return new R2dbcCrmSyncJobStore(db);}
    @Bean CrmSyncWorker crmSyncWorker(CrmSyncJobStore jobs,CrmSyncService sync,@Value("${voice.twenty.batch-size:10}") int batch){return new CrmSyncWorker(jobs,sync,batch);}
    @Bean CrmSyncScheduler crmSyncScheduler(CrmSyncWorker worker){return new CrmSyncScheduler(worker);}
    static final class CrmSyncScheduler {private final CrmSyncWorker worker;CrmSyncScheduler(CrmSyncWorker worker){this.worker=worker;}@Scheduled(fixedDelayString="${voice.twenty.poll-delay:5s}") void run(){worker.scheduledRun();}}
}
