package com.nihil.voice.audio;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class TurnAwareAudioOutputQueueTest {
    @Test
    void dropsLateChunksAndClearsBufferedAudioOnTurnChange() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var queue = new TurnAwareAudioOutputQueue(2, first);
        assertThat(queue.offer(new AudioFrame(first, new byte[]{1,2}))).isTrue();
        queue.activate(second);
        assertThat(queue.offer(new AudioFrame(first, new byte[]{3}))).isFalse();
        assertThat(queue.offer(new AudioFrame(second, new byte[]{4}))).isTrue();
        StepVerifier.create(queue.frames().take(1))
            .assertNext(frame -> assertThat(frame.payload()).containsExactly(4))
            .verifyComplete();
    }
}
