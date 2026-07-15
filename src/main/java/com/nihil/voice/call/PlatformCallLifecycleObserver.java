package com.nihil.voice.call;

import com.nihil.voice.asterisk.MediaConnection;
import com.nihil.voice.persistence.ReactiveCallStore;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

public final class PlatformCallLifecycleObserver implements CallLifecycleObserver {
    private static final Logger log=LoggerFactory.getLogger(PlatformCallLifecycleObserver.class);
    private final VoiceCallCoordinator coordinator;private final ReactiveCallStore store;private final UUID tenantId;private final UUID assistantId;
    private final ConcurrentMap<UUID,Disposable> active=new ConcurrentHashMap<>();
    public PlatformCallLifecycleObserver(VoiceCallCoordinator coordinator,ReactiveCallStore store,UUID tenantId,UUID assistantId){
        if(coordinator==null)throw new IllegalArgumentException("VoiceCallCoordinator is required when the voice pipeline is enabled");
        this.coordinator=coordinator;this.store=Objects.requireNonNull(store,"ReactiveCallStore");this.tenantId=tenantId;this.assistantId=assistantId;
    }
    @Override public Mono<Void> onReady(CallSession call,MediaConnection media){
        log.info("Call ready; persisting and starting voice pipeline callId={} mediaChannelId={}",call.internalCallId(),call.mediaChannelId());
        return store.create(call,tenantId,assistantId).then(Mono.fromRunnable(()->{
            Disposable subscription=coordinator.run(call,media)
                .doOnSubscribe(ignored->log.info("Voice STT pipeline started callId={}",call.internalCallId()))
                .subscribe(null,error->log.error("Voice pipeline failed callId={}",call.internalCallId(),error));
            Disposable old=active.put(call.internalCallId(),subscription);if(old!=null)old.dispose();
        }));
    }
    @Override public Mono<Void> onEnding(CallSession call,String reason){
        return Mono.fromRunnable(()->{Disposable subscription=active.remove(call.internalCallId());if(subscription!=null)subscription.dispose();})
            .then(store.end(call.internalCallId(),reason,reason,Instant.now()))
            .then(store.enqueuePostCall(call.internalCallId()));
    }
}
