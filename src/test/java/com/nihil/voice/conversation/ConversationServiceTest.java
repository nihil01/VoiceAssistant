package com.nihil.voice.conversation;

import static org.assertj.core.api.Assertions.*;
import com.nihil.voice.audio.AudioFrame;
import com.nihil.voice.call.CallSession;
import com.nihil.voice.call.CallState;
import com.nihil.voice.llm.LlmClient;
import com.nihil.voice.tts.TtsClient;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class ConversationServiceTest {
    @Test
    void finalTranscriptProducesOrderedTurnScopedAudioAndReturnsToListening() {
        CallSession call = CallSession.create(UUID.randomUUID(), "caller", "+994****0000", "700");
        call.transitionTo(CallState.ANSWERED);
        call.transitionTo(CallState.LISTENING);
        LlmClient llm = messages -> Flux.just("Salam", ", ", "necə", "siniz?");
        TtsClient tts = text -> Flux.just(new byte[]{1}, new byte[]{2});
        var service = new ConversationService(llm, tts, new TurnCancellationRegistry());

        StepVerifier.create(service.respond(call, "Salam", "stt-event-1"))
            .assertNext(frame -> assertThat(frame.payload()).containsExactly(1))
            .assertNext(frame -> assertThat(frame.payload()).containsExactly(2))
            .verifyComplete();

        assertThat(call.state()).isEqualTo(CallState.LISTENING);
        assertThat(call.currentTurnId()).isNotNull();
    }

    @Test
    void duplicateFinalTranscriptEventIsIgnored() {
        CallSession call = CallSession.create(UUID.randomUUID(), "caller", null, null);
        call.transitionTo(CallState.ANSWERED);
        call.transitionTo(CallState.LISTENING);
        var service = new ConversationService(m -> Flux.just("ok"), t -> Flux.just(new byte[]{1}), new TurnCancellationRegistry());
        StepVerifier.create(service.respond(call, "hello", "same-id")).expectNextCount(1).verifyComplete();
        StepVerifier.create(service.respond(call, "hello", "same-id")).verifyComplete();
    }
}
