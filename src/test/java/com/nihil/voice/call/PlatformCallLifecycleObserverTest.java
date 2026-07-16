package com.nihil.voice.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nihil.voice.asterisk.MediaConnection;
import com.nihil.voice.audio.AudioFrame;
import com.nihil.voice.conversation.ConversationService;
import com.nihil.voice.conversation.TurnCancellationRegistry;
import com.nihil.voice.persistence.CallMessageData;
import com.nihil.voice.persistence.ReactiveCallStore;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PlatformCallLifecycleObserverTest {
    @Test
    void rejectsMissingVoiceCoordinatorInsteadOfSilentlyDisablingPipeline() {
        assertThatThrownBy(() -> new PlatformCallLifecycleObserver(null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VoiceCallCoordinator");
    }

    @Test
    void doesNotEndOrEnqueuePostCallForCallThatNeverBecameReady() {
        var store = new RecordingCallStore();
        var observer = new PlatformCallLifecycleObserver(coordinator(), store, null, null);
        CallSession call = CallSession.create(UUID.randomUUID(), "caller", null, null);

        StepVerifier.create(observer.onEnding(call, "setup_failed")).verifyComplete();

        assertThat(store.endCalls).isZero();
        assertThat(store.postCallJobs).isZero();
    }

    @Test
    void persistsNormalizedTerminalStatusAfterReadyCallEnds() {
        var store = new RecordingCallStore();
        var observer = new PlatformCallLifecycleObserver(coordinator(), store, null, null);
        CallSession call = CallSession.create(UUID.randomUUID(), "caller", null, null);
        call.transitionTo(CallState.ANSWERED);
        call.transitionTo(CallState.LISTENING);

        StepVerifier.create(observer.onReady(call, new EmptyMediaConnection())).verifyComplete();
        StepVerifier.create(observer.onEnding(call, "CHANNEL_DESTROYED")).verifyComplete();

        assertThat(store.status).isEqualTo("ENDED");
        assertThat(store.cause).isEqualTo("CHANNEL_DESTROYED");
        assertThat(store.postCallJobs).isEqualTo(1);
    }

    private static VoiceCallCoordinator coordinator() {
        var cancellations = new TurnCancellationRegistry();
        var conversation = new ConversationService(
                messages -> Flux.empty(),
                text -> Flux.empty(),
                cancellations
        );
        return new VoiceCallCoordinator(audio -> Flux.empty(), conversation, cancellations);
    }

    private static final class EmptyMediaConnection implements MediaConnection {
        @Override
        public Flux<byte[]> inboundAudio() {
            return Flux.empty();
        }

        @Override
        public void activateTurn(UUID turnId) {}

        @Override
        public boolean send(AudioFrame frame) {
            return true;
        }

        @Override
        public void clearBuffer(UUID nextTurnId) {}

        @Override
        public Mono<Void> close() {
            return Mono.empty();
        }
    }

    private static final class RecordingCallStore implements ReactiveCallStore {
        int endCalls;
        int postCallJobs;
        String status;
        String cause;

        @Override
        public Mono<Void> create(CallSession call, UUID tenantId, UUID assistantId) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> appendMessage(CallMessageData message) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> end(
                UUID callId,
                String status,
                String hangupCause,
                Instant endedAt
        ) {
            return Mono.fromRunnable(() -> {
                endCalls++;
                this.status = status;
                cause = hangupCause;
            });
        }

        @Override
        public Mono<Void> enqueueCrmSync(
                UUID callId,
                UUID tenantId,
                String externalCallId
        ) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> enqueuePostCall(UUID callId) {
            return Mono.fromRunnable(() -> postCallJobs++);
        }
    }
}
