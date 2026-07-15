package com.nihil.voice.call;

import com.nihil.voice.asterisk.MediaConnection;
import reactor.core.publisher.Mono;

public interface CallLifecycleObserver {
    CallLifecycleObserver NOOP=new CallLifecycleObserver(){public Mono<Void> onReady(CallSession call,MediaConnection media){return Mono.empty();} public Mono<Void> onEnding(CallSession call,String reason){return Mono.empty();}};
    Mono<Void> onReady(CallSession call,MediaConnection media);
    Mono<Void> onEnding(CallSession call,String reason);
}
