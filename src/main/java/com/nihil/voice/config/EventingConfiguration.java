package com.nihil.voice.config;
import com.nihil.voice.events.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
@Configuration(proxyBeanMethods=false) @EnableScheduling
public class EventingConfiguration {
 @Bean EventOutboxStore eventOutboxStore(DatabaseClient db){return new R2dbcEventOutboxStore(db);}
 @Bean DomainEventTransport domainEventTransport(ReactiveStringRedisTemplate redis,@Value("${voice.events.stream:voice-domain-events}") String stream){return new RedisStreamEventTransport(redis,stream);}
 @Bean OutboxRelay outboxRelay(EventOutboxStore store,DomainEventTransport transport,@Value("${voice.events.batch-size:100}") int batch){return new OutboxRelay(store,transport,batch);}
 @Bean OutboxRelayScheduler outboxRelayScheduler(OutboxRelay relay){return new OutboxRelayScheduler(relay);}
 @Bean RelayTick relayTick(OutboxRelayScheduler scheduler){return new RelayTick(scheduler);}
 static final class RelayTick {private final OutboxRelayScheduler scheduler;RelayTick(OutboxRelayScheduler s){scheduler=s;}@Scheduled(fixedDelayString="${voice.events.poll-delay:1s}") void tick(){scheduler.run();}}
}
