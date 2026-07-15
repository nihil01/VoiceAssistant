package com.nihil.voice.events;
import reactor.core.publisher.Flux;
public final class OutboxRelay {
 private final EventOutboxStore store;private final DomainEventTransport transport;private final int batch;
 public OutboxRelay(EventOutboxStore store,DomainEventTransport transport,int batch){this.store=store;this.transport=transport;this.batch=batch;}
 public Flux<String> runOnce(){return store.pending(batch).concatMap(event->transport.publish(event).flatMap(id->store.markPublished(event.id()).thenReturn(id)));}
}
