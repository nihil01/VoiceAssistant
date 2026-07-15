package com.nihil.voice.asterisk;

import com.nihil.voice.audio.AudioFrame;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/** Per-call bounded, serialized media queues and chan_websocket flow control. */
public final class MediaChannel {
    public record OutboundPayload(UUID turnId, byte[] bytes, String text) {
        public OutboundPayload { if (bytes != null) bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes == null ? null : bytes.clone(); }
        public boolean binary() { return bytes != null; }
        static OutboundPayload audio(UUID turnId, byte[] bytes) { return new OutboundPayload(turnId, bytes, null); }
        static OutboundPayload command(String text) { return new OutboundPayload(null, null, text); }
    }

    private final Sinks.Many<byte[]> inbound;
    private final Sinks.Many<OutboundPayload> outbound;
    private final AtomicReference<UUID> activeTurn = new AtomicReference<>();
    private final AtomicBoolean paused = new AtomicBoolean();
    private final AtomicReference<Sinks.One<Void>> resumed = new AtomicReference<>(Sinks.one());
    private final MediaControlMessageParser controls = new MediaControlMessageParser();
    private final MeterRegistry meters;

    public MediaChannel(int capacity, MeterRegistry meters) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.meters = Objects.requireNonNull(meters);
        this.inbound = Sinks.many().unicast().onBackpressureBuffer(new ArrayBlockingQueue<>(capacity));
        this.outbound = Sinks.many().replay().limit(capacity);
    }

    public void activate(UUID turnId) { activeTurn.set(Objects.requireNonNull(turnId)); }
    public Flux<byte[]> inboundAudio() { return inbound.asFlux().map(byte[]::clone); }

    public boolean acceptBinary(byte[] payload) {
        byte[] copy = payload.clone();
        boolean accepted = inbound.tryEmitNext(copy).isSuccess();
        meters.counter(accepted ? "asterisk.media.inbound.frames" : "asterisk.media.inbound.dropped").increment();
        return accepted;
    }

    public synchronized boolean offer(UUID turnId, byte[] payload) {
        if (turnId == null || !turnId.equals(activeTurn.get())) return false;
        boolean accepted = outbound.tryEmitNext(OutboundPayload.audio(turnId, payload)).isSuccess();
        if (!accepted) meters.counter("asterisk.media.outbound.dropped").increment();
        return accepted;
    }

    public void acceptControl(String raw) {
        switch (controls.parse(raw).command()) {
            case MEDIA_XOFF -> {
                if (paused.compareAndSet(false, true)) resumed.set(Sinks.one());
            }
            case MEDIA_XON -> {
                paused.set(false);
                resumed.get().tryEmitEmpty();
            }
            default -> { }
        }
    }

    public synchronized void clearBuffer(UUID nextTurnId) {
        activeTurn.set(Objects.requireNonNull(nextTurnId));
        outbound.tryEmitNext(OutboundPayload.command("FLUSH_MEDIA"));
        meters.counter("asterisk.media.buffer.cleared").increment();
    }

    public Flux<OutboundPayload> outboundPayloads() {
        return outbound.asFlux().concatMap(payload -> {
            if (payload.binary() && !payload.turnId().equals(activeTurn.get())) return Mono.empty();
            if (!payload.binary() || !paused.get()) return Mono.just(payload);
            return resumed.get().asMono().then(Mono.defer(() ->
                payload.turnId().equals(activeTurn.get()) ? Mono.just(payload) : Mono.empty()));
        }, 1);
    }

    public void complete() {
        inbound.tryEmitComplete();
        outbound.tryEmitComplete();
        resumed.get().tryEmitEmpty();
    }
}
