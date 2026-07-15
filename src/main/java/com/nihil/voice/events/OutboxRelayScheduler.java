package com.nihil.voice.events;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;
public final class OutboxRelayScheduler {
 private static final Logger log=LoggerFactory.getLogger(OutboxRelayScheduler.class);private final OutboxRelay relay;private final AtomicBoolean running=new AtomicBoolean();
 public OutboxRelayScheduler(OutboxRelay relay){this.relay=relay;}
 public void run(){if(!running.compareAndSet(false,true))return;relay.runOnce().doOnError(e->log.warn("Outbox relay failed; events remain pending",e)).onErrorComplete().doFinally(s->running.set(false)).subscribe();}
}
