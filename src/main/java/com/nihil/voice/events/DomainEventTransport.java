package com.nihil.voice.events;
import reactor.core.publisher.Mono;
public interface DomainEventTransport { Mono<String> publish(OutboxMessage event); }
