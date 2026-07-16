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
    private static final int HISTORY_MESSAGE_LIMIT = 24;
    private static final int LLM_PREFETCH = 32;
    private static final int TTS_PHRASE_PREFETCH = 4;

    private final LlmClient llm;
    private final TtsClient tts;
    private final TurnCancellationRegistry cancellations;
    private final ConversationMessageSink messages;
    private final ConcurrentMap<UUID, Set<String>> processedEvents = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConversationHistory> histories = new ConcurrentHashMap<>();

    public ConversationService(
            LlmClient llm,
            TtsClient tts,
            TurnCancellationRegistry cancellations
    ) {
        this(llm, tts, cancellations, ConversationMessageSink.NOOP);
    }

    public ConversationService(
            LlmClient llm,
            TtsClient tts,
            TurnCancellationRegistry cancellations,
            ConversationMessageSink messages
    ) {
        this.llm = llm;
        this.tts = tts;
        this.cancellations = cancellations;
        this.messages = messages;
    }

    public Flux<AudioFrame> respond(
            CallSession call,
            String finalTranscript,
            String providerEventId
    ) {
        if (finalTranscript == null || finalTranscript.isBlank()) {
            return Flux.empty();
        }
        return Flux.defer(() -> {
            if (isDuplicate(call, providerEventId)) {
                return Flux.empty();
            }
            return startResponse(call, finalTranscript.strip(), providerEventId)
                    .doOnError(ignored -> forgetEvent(call.internalCallId(), providerEventId));
        });
    }

    public void closeCall(UUID callId) {
        histories.remove(callId);
        processedEvents.remove(callId);
        cancellations.closeCall(callId);
    }

    private Flux<AudioFrame> startResponse(
            CallSession call,
            String transcript,
            String providerEventId
    ) {
        if (call.state() != CallState.LISTENING) {
            return Flux.error(new IllegalStateException("Call is not listening"));
        }

        UUID turnId = call.startTurn();
        call.transitionTo(CallState.THINKING);

        ConversationHistory history = histories.computeIfAbsent(
                call.internalCallId(),
                ignored -> new ConversationHistory(HISTORY_MESSAGE_LIMIT)
        );
        history.add(new ConversationMessage(ConversationMessage.Role.USER, transcript));

        Mono<Void> cancellation = cancellations.register(call.internalCallId(), turnId);
        Flux<AudioFrame> response = messages
                .record(call, ConversationMessage.Role.USER, transcript, providerEventId, turnId)
                .thenMany(streamAssistantAudio(call, history, turnId));

        return response
                .takeUntilOther(cancellation)
                .doOnComplete(() -> returnToListening(call, turnId))
                .doOnError(ignored -> returnToListening(call, turnId))
                .doFinally(ignored -> cancellations.remove(call.internalCallId(), turnId));
    }

    private Flux<AudioFrame> streamAssistantAudio(
            CallSession call,
            ConversationHistory history,
            UUID turnId
    ) {
        var generatedText = new StringBuilder();
        var chunker = new StreamingTextChunker();

        Flux<String> phrases = llm.stream(history.snapshot())
                .doOnNext(generatedText::append)
                .concatMap(delta -> Flux.fromIterable(chunker.append(delta)), LLM_PREFETCH)
                .concatWith(Flux.defer(() -> Flux.fromIterable(chunker.finish())));

        Flux<AudioFrame> frames = phrases
                .concatMap(tts::synthesize, TTS_PHRASE_PREFETCH)
                .doOnNext(ignored -> markSpeaking(call, turnId))
                .map(bytes -> new AudioFrame(turnId, bytes));

        Mono<Void> persistAssistantMessage = Mono.defer(() -> {
            String text = generatedText.toString().strip();
            if (text.isBlank()) {
                return Mono.empty();
            }
            history.add(new ConversationMessage(ConversationMessage.Role.ASSISTANT, text));
            return messages.record(
                    call,
                    ConversationMessage.Role.ASSISTANT,
                    text,
                    null,
                    turnId
            );
        });

        return frames.concatWith(persistAssistantMessage.thenMany(Flux.empty()));
    }

    private boolean isDuplicate(CallSession call, String providerEventId) {
        if (providerEventId == null) {
            return false;
        }
        Set<String> callEvents = processedEvents.computeIfAbsent(
                call.internalCallId(),
                ignored -> ConcurrentHashMap.newKeySet()
        );
        return !callEvents.add(providerEventId);
    }

    private void forgetEvent(UUID callId, String providerEventId) {
        if (providerEventId == null) {
            return;
        }
        processedEvents.computeIfPresent(callId, (ignored, events) -> {
            events.remove(providerEventId);
            return events.isEmpty() ? null : events;
        });
    }

    private static void markSpeaking(CallSession call, UUID turnId) {
        if (call.isCurrentTurn(turnId) && call.state() == CallState.THINKING) {
            call.transitionTo(CallState.SPEAKING);
        }
    }

    private static void returnToListening(CallSession call, UUID turnId) {
        if (!call.isCurrentTurn(turnId)) {
            return;
        }
        if (call.state() == CallState.SPEAKING || call.state() == CallState.THINKING) {
            call.transitionTo(CallState.LISTENING);
        }
    }
}
