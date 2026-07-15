package com.nihil.voice.stt;

import reactor.core.publisher.Flux;

@FunctionalInterface
public interface SttClient { Flux<SttEvent> transcribe(Flux<byte[]> pcm16Audio); }
