package com.nihil.voice.call;

import com.nihil.voice.asterisk.MediaConnection;
import com.nihil.voice.conversation.ConversationService;
import com.nihil.voice.conversation.TurnCancellationRegistry;
import com.nihil.voice.stt.SttClient;
import com.nihil.voice.stt.SttEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public final class VoiceCallCoordinator {
    private static final Logger log = LoggerFactory.getLogger(VoiceCallCoordinator.class);
    private static final long TARGET_TIME_TO_FIRST_AUDIO_MILLIS = 2_000;

    private final SttClient stt;
    private final ConversationService conversation;
    private final TurnCancellationRegistry cancellations;
    private final MeterRegistry meters;
    private final boolean logTranscripts;

    public VoiceCallCoordinator(
            SttClient stt,
            ConversationService conversation,
            TurnCancellationRegistry cancellations
    ) {
        this(stt, conversation, cancellations, Metrics.globalRegistry, false);
    }

    public VoiceCallCoordinator(
            SttClient stt,
            ConversationService conversation,
            TurnCancellationRegistry cancellations,
            boolean logTranscripts
    ) {
        this(stt, conversation, cancellations, Metrics.globalRegistry, logTranscripts);
    }

    public VoiceCallCoordinator(
            SttClient stt,
            ConversationService conversation,
            TurnCancellationRegistry cancellations,
            MeterRegistry meters,
            boolean logTranscripts
    ) {
        this.stt = stt;
        this.conversation = conversation;
        this.cancellations = cancellations;
        this.meters = meters;
        this.logTranscripts = logTranscripts;
    }

    public Mono<Void> run(CallSession call, MediaConnection media) {
        return stt.transcribe(media.inboundAudio())
                // Do not queue VAD behind a multi-second response: barge-in must
                // cancel playback while assistant audio is still streaming.
                .flatMap(event -> handle(call, media, event), 8)
                .then();
    }

    public void closeCall(UUID callId) {
        conversation.closeCall(callId);
    }

    private Mono<Void> handle(CallSession call, MediaConnection media, SttEvent event) {
        return switch (event.type()) {
            case PARTIAL -> {
                logTranscript("PARTIAL", call, event);
                yield interruptIfResponding(call, media);
            }
            case FINAL -> handleFinalTranscript(call, media, event);
            case SPEECH_STARTED -> interruptIfResponding(call, media);
            case ERROR -> Mono.error(
                    new IllegalStateException("STT provider error: " + event.text())
            );
            default -> Mono.empty();
        };
    }

    private Mono<Void> handleFinalTranscript(
            CallSession call,
            MediaConnection media,
            SttEvent event
    ) {
        logTranscript("FINAL", call, event);
        long startedNanos = System.nanoTime();
        var firstAudio = new AtomicBoolean();

        return conversation.respond(call, event.text(), event.eventId())
                .doOnSubscribe(ignored -> activateOutputTurn(call, media))
                .doOnNext(ignored -> recordFirstAudio(call, startedNanos, firstAudio))
                .handle((frame, sink) -> {
                    if (media.send(frame)) {
                        sink.next(frame);
                    } else {
                        sink.error(new IllegalStateException(
                                "Asterisk media output queue rejected audio"
                        ));
                    }
                })
                .doFinally(ignored -> meters.timer("voice.turn.pipeline.duration")
                        .record(System.nanoTime() - startedNanos, TimeUnit.NANOSECONDS))
                .then();
    }

    private void activateOutputTurn(CallSession call, MediaConnection media) {
        UUID turnId = call.currentTurnId();
        if (turnId == null) {
            throw new IllegalStateException("Conversation started without an active turn");
        }
        media.activateTurn(turnId);
        log.debug(
                "Activated Asterisk media output turn callId={} turnId={}",
                call.internalCallId(),
                turnId
        );
    }

    private void recordFirstAudio(
            CallSession call,
            long startedNanos,
            AtomicBoolean firstAudio
    ) {
        if (!firstAudio.compareAndSet(false, true)) {
            return;
        }

        long elapsedNanos = System.nanoTime() - startedNanos;
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        meters.timer("voice.turn.time_to_first_audio")
                .record(elapsedNanos, TimeUnit.NANOSECONDS);

        if (elapsedMillis > TARGET_TIME_TO_FIRST_AUDIO_MILLIS) {
            log.warn(
                    "Voice response missed TTFA target callId={} elapsedMs={} targetMs={}",
                    call.internalCallId(),
                    elapsedMillis,
                    TARGET_TIME_TO_FIRST_AUDIO_MILLIS
            );
        } else {
            log.info(
                    "Voice response first audio callId={} elapsedMs={}",
                    call.internalCallId(),
                    elapsedMillis
            );
        }
    }

    private void logTranscript(String type, CallSession call, SttEvent event) {
        if (!logTranscripts) {
            return;
        }
        String text = event.text() == null
                ? ""
                : event.text().replace('\n', ' ').replace('\r', ' ');
        if (text.length() > 500) {
            text = text.substring(0, 500) + "…";
        }
        log.info(
                "STT {} callId={} eventId={} text={}",
                type,
                call.internalCallId(),
                event.eventId(),
                text
        );
    }

    private Mono<Void> interruptIfResponding(CallSession call, MediaConnection media) {
        if (call.state() != CallState.SPEAKING && call.state() != CallState.THINKING) {
            return Mono.empty();
        }

        UUID interruptedTurn = call.currentTurnId();
        // Invalidate the turn before signalling Reactor cancellation. Cancellation
        // completes synchronously and its completion hook would otherwise race this
        // transition and move the call back to LISTENING first.
        UUID replacementTurn = call.interruptAndStartListening();
        cancellations.cancel(call.internalCallId(), interruptedTurn);
        media.clearBuffer(replacementTurn);
        meters.counter("voice.turn.barge_in").increment();
        log.info(
                "Caller interrupted voice response callId={} interruptedTurn={} replacementTurn={}",
                call.internalCallId(), interruptedTurn, replacementTurn
        );
        return Mono.empty();
    }
}
