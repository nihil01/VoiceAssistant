package com.nihil.voice.audio;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

public final class TurnAwareAudioOutputQueue {
    private final int capacity;
    private final AtomicReference<UUID> activeTurn;
    private final AtomicReference<Sinks.Many<AudioFrame>> sink;

    public TurnAwareAudioOutputQueue(int capacity, UUID initialTurn) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        this.activeTurn = new AtomicReference<>(Objects.requireNonNull(initialTurn));
        this.sink = new AtomicReference<>(newSink());
    }

    public synchronized void activate(UUID turnId) {
        activeTurn.set(Objects.requireNonNull(turnId));
        Sinks.Many<AudioFrame> previous = sink.getAndSet(newSink());
        previous.tryEmitComplete();
    }

    public boolean offer(AudioFrame frame) {
        if (!activeTurn.get().equals(frame.turnId())) return false;
        return sink.get().tryEmitNext(frame).isSuccess();
    }

    public Flux<AudioFrame> frames() { return Flux.defer(() -> sink.get().asFlux()); }
    public UUID activeTurn() { return activeTurn.get(); }
    private Sinks.Many<AudioFrame> newSink() { return Sinks.many().replay().limit(capacity); }
}
