package com.nihil.voice.interruption;

import com.nihil.voice.call.CallSession;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface MediaBufferController { Mono<Void> clear(CallSession call); }
