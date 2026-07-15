package com.nihil.voice.events;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
public interface EventOutboxStore { Flux<OutboxMessage> pending(int limit); Mono<Void> markPublished(UUID eventId); }
