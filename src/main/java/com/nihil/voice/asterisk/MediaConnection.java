package com.nihil.voice.asterisk;

import com.nihil.voice.audio.AudioFrame;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MediaConnection {
    Flux<byte[]> inboundAudio();
    void activateTurn(UUID turnId);
    boolean send(AudioFrame frame);
    void clearBuffer(UUID nextTurnId);
    Mono<Void> close();
}
