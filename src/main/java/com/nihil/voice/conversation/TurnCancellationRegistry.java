package com.nihil.voice.conversation;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public final class TurnCancellationRegistry {
    private final ConcurrentMap<UUID, TurnControl> controls = new ConcurrentHashMap<>();

    public Mono<Void> register(UUID callId, UUID turnId) {
        TurnControl control = new TurnControl(turnId, Sinks.empty());
        TurnControl old = controls.put(callId, control);
        if (old != null) old.signal.tryEmitEmpty();
        return control.signal.asMono();
    }

    public boolean cancel(UUID callId, UUID turnId) {
        TurnControl control = controls.get(callId);
        return control != null && control.turnId.equals(turnId) && control.signal.tryEmitEmpty().isSuccess();
    }

    public void remove(UUID callId, UUID turnId) {
        controls.computeIfPresent(callId, (ignored, control) -> control.turnId.equals(turnId) ? null : control);
    }

    private record TurnControl(UUID turnId, Sinks.Empty<Void> signal) {}
}
