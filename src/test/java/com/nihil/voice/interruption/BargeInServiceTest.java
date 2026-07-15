package com.nihil.voice.interruption;

import static org.assertj.core.api.Assertions.*;
import com.nihil.voice.audio.AudioFrame;
import com.nihil.voice.audio.TurnAwareAudioOutputQueue;
import com.nihil.voice.call.CallSession;
import com.nihil.voice.call.CallState;
import com.nihil.voice.conversation.TurnCancellationRegistry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BargeInServiceTest {
    @Test
    void cancelsCurrentTurnClearsQueueAndRequestsRemoteBufferFlush() {
        var call = CallSession.create(UUID.randomUUID(), "caller", null, null);
        call.transitionTo(CallState.ANSWERED);
        call.transitionTo(CallState.LISTENING);
        UUID oldTurn = call.startTurn();
        call.transitionTo(CallState.THINKING);
        call.transitionTo(CallState.SPEAKING);
        var queue = new TurnAwareAudioOutputQueue(8, oldTurn);
        queue.offer(new AudioFrame(oldTurn, new byte[]{1}));
        var cancellations = new TurnCancellationRegistry();
        cancellations.register(call.internalCallId(), oldTurn);
        var flushes = new AtomicInteger();
        var service = new BargeInService(cancellations, ignored -> { flushes.incrementAndGet(); return reactor.core.publisher.Mono.empty(); });

        reactor.test.StepVerifier.create(service.interrupt(call, queue))
            .assertNext(newTurn -> {
                assertThat(newTurn).isNotEqualTo(oldTurn);
                assertThat(queue.activeTurn()).isEqualTo(newTurn);
            })
            .verifyComplete();

        assertThat(call.state()).isEqualTo(CallState.LISTENING);
        assertThat(flushes).hasValue(1);
    }
}
