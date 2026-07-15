package com.nihil.voice.interruption;

import com.nihil.voice.audio.TurnAwareAudioOutputQueue;
import com.nihil.voice.call.CallSession;
import com.nihil.voice.call.CallState;
import com.nihil.voice.conversation.TurnCancellationRegistry;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class BargeInService {
    private final TurnCancellationRegistry cancellations;
    private final MediaBufferController mediaBuffer;

    public BargeInService(TurnCancellationRegistry cancellations, MediaBufferController mediaBuffer) {
        this.cancellations = cancellations;
        this.mediaBuffer = mediaBuffer;
    }

    public Mono<UUID> interrupt(CallSession call, TurnAwareAudioOutputQueue output) {
        if (call.state() != CallState.SPEAKING) return Mono.empty();
        UUID previousTurn = call.currentTurnId();
        cancellations.cancel(call.internalCallId(), previousTurn);
        UUID newTurn = call.interruptAndStartListening();
        output.activate(newTurn);
        return mediaBuffer.clear(call).thenReturn(newTurn);
    }
}
