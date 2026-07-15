package com.nihil.voice.events;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OutboxRelayTest {
    @Test void marksEventOnlyAfterTransportPublishesIt() {
        UUID id=UUID.randomUUID();var marked=new ArrayList<UUID>();
        EventOutboxStore store=new EventOutboxStore(){
            public Flux<OutboxMessage> pending(int limit){return Flux.just(new OutboxMessage(id,"CallEnded","{\"callId\":\"1\"}"));}
            public Mono<Void> markPublished(UUID eventId){return Mono.fromRunnable(()->marked.add(eventId));}
        };
        DomainEventTransport transport=event->Mono.just("redis-1");

        StepVerifier.create(new OutboxRelay(store,transport,10).runOnce()).expectNext("redis-1").verifyComplete();
        assertThat(marked).containsExactly(id);
    }

    @Test void failedPublishLeavesEventPending() {
        UUID id=UUID.randomUUID();var marked=new ArrayList<UUID>();
        EventOutboxStore store=new EventOutboxStore(){
            public Flux<OutboxMessage> pending(int limit){return Flux.just(new OutboxMessage(id,"CallEnded","{}"));}
            public Mono<Void> markPublished(UUID eventId){return Mono.fromRunnable(()->marked.add(eventId));}
        };
        DomainEventTransport transport=event->Mono.error(new IllegalStateException("redis unavailable"));

        StepVerifier.create(new OutboxRelay(store,transport,10).runOnce()).expectErrorMessage("redis unavailable").verify();
        assertThat(marked).isEmpty();
    }
}
