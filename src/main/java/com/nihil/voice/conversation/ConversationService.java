package com.nihil.voice.conversation;

import com.nihil.voice.audio.AudioFrame;
import com.nihil.voice.call.CallSession;
import com.nihil.voice.call.CallState;
import com.nihil.voice.call.ConversationMessageSink;
import com.nihil.voice.llm.ConversationMessage;
import com.nihil.voice.llm.LlmClient;
import com.nihil.voice.tts.TtsClient;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class ConversationService {
    private final LlmClient llm;
    private final TtsClient tts;
    private final TurnCancellationRegistry cancellations;
    private final ConversationMessageSink messages;
    private final Set<String> processedEvents = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, ConversationHistory> histories = new ConcurrentHashMap<>();

    public ConversationService(LlmClient llm, TtsClient tts, TurnCancellationRegistry cancellations) {
        this(llm, tts, cancellations, ConversationMessageSink.NOOP);
    }
    public ConversationService(LlmClient llm, TtsClient tts, TurnCancellationRegistry cancellations, ConversationMessageSink messages) {
        this.llm = llm;
        this.tts = tts;
        this.cancellations = cancellations;
        this.messages = messages;
    }

    public Flux<AudioFrame> respond(CallSession call, String finalTranscript, String providerEventId) {
        if (finalTranscript == null || finalTranscript.isBlank()) return Flux.empty();
        String dedupKey = call.internalCallId() + ":" + providerEventId;
        if (providerEventId != null && !processedEvents.add(dedupKey)) return Flux.empty();

        return Flux.defer(() -> {
            if (call.state() != CallState.LISTENING) return Flux.error(new IllegalStateException("Call is not listening"));
            UUID turnId = call.startTurn();
            call.transitionTo(CallState.THINKING);
            ConversationHistory history = histories.computeIfAbsent(call.internalCallId(), ignored -> new ConversationHistory(24));
            history.add(new ConversationMessage(ConversationMessage.Role.USER, finalTranscript.strip()));
            var cancellation = cancellations.register(call.internalCallId(), turnId);

            return messages.record(call, ConversationMessage.Role.USER, finalTranscript.strip(), providerEventId, turnId)
                .thenMany(llm.stream(history.snapshot()))
                .collectList()
                .map(tokens -> String.join("", tokens).strip())
                .filter(text -> !text.isBlank())
                .doOnNext(text -> { history.add(new ConversationMessage(ConversationMessage.Role.ASSISTANT, text)); if (call.isCurrentTurn(turnId) && call.state() == CallState.THINKING) call.transitionTo(CallState.SPEAKING); })
                .flatMapMany(text -> messages.record(call, ConversationMessage.Role.ASSISTANT, text, null, turnId).thenMany(tts.synthesize(text)))
                .map(bytes -> new AudioFrame(turnId, bytes))
                .takeUntilOther(cancellation)
                .doOnComplete(() -> {
                    if (call.isCurrentTurn(turnId) && (call.state() == CallState.SPEAKING || call.state() == CallState.THINKING)) {
                        call.transitionTo(CallState.LISTENING);
                    }
                })
                .doOnError(error -> {
                    if (call.isCurrentTurn(turnId) && (call.state() == CallState.SPEAKING || call.state() == CallState.THINKING)) {
                        call.transitionTo(CallState.LISTENING);
                    }
                })
                .doFinally(ignored -> cancellations.remove(call.internalCallId(), turnId));
        });
    }
}
