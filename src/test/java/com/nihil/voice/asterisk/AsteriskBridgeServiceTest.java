package com.nihil.voice.asterisk;

import static org.assertj.core.api.Assertions.assertThat;

import com.nihil.voice.call.CallSession;
import com.nihil.voice.call.CallSessionManager;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AsteriskBridgeServiceTest {
    @Test
    void setupTimesOutAndCleansResourcesWhenMediaNeverEntersStasis() {
        var calls = new CallSessionManager();
        var operations = new ArrayList<String>();
        var service = new AsteriskBridgeService(new RecordingAriGateway(operations),
            new RecordingMediaGateway(operations), calls, com.nihil.voice.call.CallLifecycleObserver.NOOP,
            Duration.ofMillis(50));

        StepVerifier.create(service.startCaller(new AriChannel("caller-timeout", "PJSIP/700-2", "+9942", "700")))
            .expectError(java.util.concurrent.TimeoutException.class)
            .verify();

        assertThat(operations).containsSequence("closeMedia", "deleteChannel:media-1", "deleteBridge:bridge-1");
        assertThat(calls.activeCount()).isZero();
    }

    @Test
    void mediaIsAddedOnlyAfterItsStasisStartAndCleanupIsOrderedAndIdempotent() {
        var calls = new CallSessionManager();
        var operations = new ArrayList<String>();
        var ari = new RecordingAriGateway(operations);
        var media = new RecordingMediaGateway(operations);
        var service = new AsteriskBridgeService(ari, media, calls);

        Mono<CallSession> setup = service.startCaller(new AriChannel("caller-1", "PJSIP/700-1", "+9941", "700"));
        StepVerifier.create(setup)
            .then(() -> {
                assertThat(operations).containsExactly(
                    "answer:caller-1", "createBridge", "add:caller-1", "externalMedia", "variable", "connect");
                assertThat(operations).noneMatch(value -> value.equals("add:media-1"));
                service.mediaEnteredStasis("media-1");
            })
            .assertNext(call -> assertThat(call.mediaChannelId()).isEqualTo("media-1"))
            .verifyComplete();

        assertThat(operations).containsExactly(
            "answer:caller-1", "createBridge", "add:caller-1", "externalMedia", "variable", "connect", "add:media-1");

        var handler = new AriEventHandler(service);
        var mediaDestroyed = new AriEvent(AriEventType.CHANNEL_DESTROYED,
            new AriChannel("media-1", "WebSocket/INCOMING/1", null, null), java.util.List.of(), null);
        StepVerifier.create(handler.handle(mediaDestroyed)).verifyComplete();
        assertThat(calls.activeCount()).as("media destruction must clean the whole call").isZero();
        StepVerifier.create(service.cleanupByChannel("caller-1", "duplicate")).verifyComplete();
        assertThat(operations).containsSequence("closeMedia", "deleteChannel:media-1", "deleteBridge:bridge-1");
        assertThat(operations.stream().filter(value -> value.equals("deleteBridge:bridge-1"))).hasSize(1);
        assertThat(calls.activeCount()).isZero();
    }

    private static final class RecordingAriGateway implements AriGateway {
        private final List<String> operations;
        RecordingAriGateway(List<String> operations) { this.operations = operations; }
        public Mono<Void> answer(String channelId) { operations.add("answer:" + channelId); return Mono.empty(); }
        public Mono<String> createBridge(String bridgeId, String name) { operations.add("createBridge"); return Mono.just("bridge-1"); }
        public Mono<Void> addChannel(String bridgeId, String channelId) { operations.add("add:" + channelId); return Mono.empty(); }
        public Mono<AriChannel> createExternalMedia(String channelId, String callId) { operations.add("externalMedia"); return Mono.just(new AriChannel("media-1", "WebSocket/media-1", null, null)); }
        public Mono<String> channelVariable(String channelId, String variable) { operations.add("variable"); return Mono.just("connection-1"); }
        public Mono<Void> deleteChannel(String channelId) { operations.add("deleteChannel:" + channelId); return Mono.empty(); }
        public Mono<Void> deleteBridge(String bridgeId) { operations.add("deleteBridge:" + bridgeId); return Mono.empty(); }
    }

    private static final class RecordingMediaGateway implements MediaGateway {
        private final List<String> operations;
        RecordingMediaGateway(List<String> operations) { this.operations = operations; }
        public Mono<MediaConnection> connect(CallSession call) {
            operations.add("connect");
            return Mono.just(new MediaConnection() {
                public reactor.core.publisher.Flux<byte[]> inboundAudio() { return reactor.core.publisher.Flux.never(); }
                public void activateTurn(java.util.UUID turnId) { operations.add("activateTurn"); }
                public boolean send(com.nihil.voice.audio.AudioFrame frame) { return true; }
                public void clearBuffer(java.util.UUID nextTurnId) { operations.add("clearBuffer"); }
                public Mono<Void> close() { operations.add("closeMedia"); return Mono.empty(); }
            });
        }
    }
}
