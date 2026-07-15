package com.nihil.voice.asterisk;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class MediaChannelTest {
    @Test
    void inboundIsBoundedAndXoffXonAndBargeInControlOutbound() {
        var registry = new SimpleMeterRegistry();
        var channel = new MediaChannel(2, registry);
        UUID turn = UUID.randomUUID();
        channel.activate(turn);

        StepVerifier.create(channel.inboundAudio().take(2), 0)
            .then(() -> {
                assertThat(channel.acceptBinary(new byte[]{1})).isTrue();
                assertThat(channel.acceptBinary(new byte[]{2})).isTrue();
                assertThat(channel.acceptBinary(new byte[]{3})).isFalse();
            })
            .thenRequest(2)
            .expectNextMatches(bytes -> bytes[0] == 1)
            .expectNextMatches(bytes -> bytes[0] == 2)
            .verifyComplete();
        assertThat(registry.counter("asterisk.media.inbound.dropped").count()).isEqualTo(1);

        channel.acceptControl("MEDIA_XOFF");
        StepVerifier.create(channel.outboundPayloads().take(1))
            .then(() -> assertThat(channel.offer(turn, new byte[]{9})).isTrue())
            .expectNoEvent(Duration.ofMillis(20))
            .then(() -> channel.acceptControl("MEDIA_XON"))
            .expectNextMatches(payload -> payload.binary() && payload.bytes()[0] == 9)
            .verifyComplete();

        UUID next = UUID.randomUUID();
        channel.clearBuffer(next);
        StepVerifier.create(channel.outboundPayloads().take(1))
            .assertNext(payload -> assertThat(payload.text()).isEqualTo("FLUSH_MEDIA"))
            .verifyComplete();
        assertThat(channel.offer(turn, new byte[]{8})).isFalse();
    }
}
