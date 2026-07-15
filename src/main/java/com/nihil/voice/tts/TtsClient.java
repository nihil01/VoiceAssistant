package com.nihil.voice.tts;

import reactor.core.publisher.Flux;

@FunctionalInterface
public interface TtsClient { Flux<byte[]> synthesize(String text); }
