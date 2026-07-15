package com.nihil.voice.call;

import com.nihil.voice.asterisk.MediaConnection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

public final class VoiceCallPipelineLifecycle implements CallLifecycleObserver {
    private static final Logger log=LoggerFactory.getLogger(VoiceCallPipelineLifecycle.class);
    private final VoiceCallCoordinator coordinator;private final ConcurrentMap<UUID,Disposable> active=new ConcurrentHashMap<>();
    public VoiceCallPipelineLifecycle(VoiceCallCoordinator coordinator){this.coordinator=coordinator;}
    @Override public Mono<Void> onReady(CallSession call,MediaConnection media){return Mono.fromRunnable(()->{
        Disposable subscription=coordinator.run(call,media).subscribe(null,error->log.error("Voice pipeline failed callId={}",call.internalCallId(),error));
        Disposable old=active.put(call.internalCallId(),subscription);if(old!=null)old.dispose();
    });}
    @Override public Mono<Void> onEnding(CallSession call,String reason){return Mono.fromRunnable(()->{Disposable subscription=active.remove(call.internalCallId());if(subscription!=null)subscription.dispose();});}
}
