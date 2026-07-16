package com.nihil.voice.conversation;

import static org.assertj.core.api.Assertions.*;
import com.nihil.voice.audio.AudioFrame;
import com.nihil.voice.call.CallSession;
import com.nihil.voice.call.CallState;
import com.nihil.voice.llm.LlmClient;
import com.nihil.voice.tts.TtsClient;
import java.util.ArrayList;
import java.util.List;
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
    void streamsCompletePhrasesToTtsInsteadOfWaitingForTheWholeLlmResponse() {
        CallSession call = CallSession.create(UUID.randomUUID(), "caller", null, null);
        call.transitionTo(CallState.ANSWERED);
        call.transitionTo(CallState.LISTENING);
        List<String> synthesized = new ArrayList<>();
        LlmClient llm = messages -> Flux.just("Salam", ". ", "Sizə necə ", "kömək edə bilərəm?");
        TtsClient tts = text -> {
            synthesized.add(text);
            return Flux.just(new byte[]{(byte) synthesized.size()});
        };
        var service = new ConversationService(llm, tts, new TurnCancellationRegistry());

        StepVerifier.create(service.respond(call, "Salam", "streaming-event"))
                .expectNextCount(2)
                .verifyComplete();

        assertThat(synthesized).containsExactly("Salam.", "Sizə necə kömək edə bilərəm?");
    }

    @Test
    void closeCallReleasesHistoryAndEventDeduplicationState() {
        CallSession call = CallSession.create(UUID.randomUUID(), "caller", null, null);
        call.transitionTo(CallState.ANSWERED);
        call.transitionTo(CallState.LISTENING);
        List<Integer> historySizes = new ArrayList<>();
        LlmClient llm = messages -> {
            historySizes.add(messages.size());
            return Flux.just("ok");
        };
        var service = new ConversationService(
                llm,
                text -> Flux.just(new byte[]{1}),
                new TurnCancellationRegistry()
        );

        StepVerifier.create(service.respond(call, "first", "event-1"))
                .expectNextCount(1)
                .verifyComplete();
        service.closeCall(call.internalCallId());
        StepVerifier.create(service.respond(call, "first", "event-1"))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(historySizes).containsExactly(1, 1);
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
