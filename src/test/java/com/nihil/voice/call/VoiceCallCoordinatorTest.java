package com.nihil.voice.call;

import static org.assertj.core.api.Assertions.*;
import com.nihil.voice.asterisk.MediaConnection;
import com.nihil.voice.audio.AudioFrame;
import com.nihil.voice.conversation.ConversationService;
import com.nihil.voice.conversation.TurnCancellationRegistry;
import com.nihil.voice.stt.SttClient;
import com.nihil.voice.stt.SttEvent;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class VoiceCallCoordinatorTest {
    @Test void logsPartialAndFinalTranscriptsWhenExplicitlyEnabled() {
        var call=CallSession.create(UUID.randomUUID(),"caller",null,null);
        call.transitionTo(CallState.ANSWERED);call.transitionTo(CallState.LISTENING);
        SttClient stt=audio->Flux.just(
            new SttEvent(SttEvent.Type.PARTIAL,"event-p","Sala",null,null),
            new SttEvent(SttEvent.Type.FINAL,"event-f","Salam",null,null));
        var cancellations=new TurnCancellationRegistry();
        var conversation=new ConversationService(messages->Flux.empty(),text->Flux.empty(),cancellations);
        var logger=(Logger)LoggerFactory.getLogger(VoiceCallCoordinator.class);
        var appender=new ListAppender<ILoggingEvent>();appender.start();logger.addAppender(appender);
        try {
            StepVerifier.create(new VoiceCallCoordinator(stt,conversation,cancellations,true)
                .run(call,new RecordingMediaConnection())).verifyComplete();
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(line->line.contains("STT PARTIAL")&&line.contains("Sala"))
                .anyMatch(line->line.contains("STT FINAL")&&line.contains("Salam"));
        } finally { logger.detachAppender(appender); }
    }

    @Test void finalTranscriptFlowsThroughConversationAndBackToMedia() {
        var call=CallSession.create(UUID.randomUUID(),"caller",null,null);
        call.transitionTo(CallState.ANSWERED);call.transitionTo(CallState.LISTENING);
        SttClient stt=audio->Flux.just(new SttEvent(SttEvent.Type.FINAL,"event-1","Salam",null,null));
        var cancellations=new TurnCancellationRegistry();
        var recorded=new ArrayList<String>();
        ConversationMessageSink sink=(session,role,text,eventId,turnId)->Mono.fromRunnable(()->recorded.add(role+":"+text));
        var conversation=new ConversationService(messages->Flux.just("Salam!"),text->Flux.just(new byte[]{1,2}),cancellations,sink);
        var media=new RecordingMediaConnection();
        var coordinator=new VoiceCallCoordinator(stt,conversation,cancellations);

        StepVerifier.create(coordinator.run(call,media)).verifyComplete();
        assertThat(media.sent).hasSize(1);
        assertThat(media.activations).isEqualTo(1);
        assertThat(recorded).containsExactly("USER:Salam","ASSISTANT:Salam!");
        assertThat(call.state()).isEqualTo(CallState.LISTENING);
    }

    @Test void speechStartedDuringPlaybackCancelsTurnAndFlushesAsteriskBuffer() {
        var call=CallSession.create(UUID.randomUUID(),"caller",null,null);
        call.transitionTo(CallState.ANSWERED);call.transitionTo(CallState.LISTENING);
        UUID turn=call.startTurn();call.transitionTo(CallState.THINKING);call.transitionTo(CallState.SPEAKING);
        var cancellations=new TurnCancellationRegistry();cancellations.register(call.internalCallId(),turn);
        SttClient stt=audio->Flux.just(new SttEvent(SttEvent.Type.SPEECH_STARTED,"event-2",null,null,null));
        var media=new RecordingMediaConnection();
        var coordinator=new VoiceCallCoordinator(stt,new ConversationService(m->Flux.empty(),t->Flux.empty(),cancellations),cancellations);

        StepVerifier.create(coordinator.run(call,media)).verifyComplete();
        assertThat(media.clears).isEqualTo(1);
        assertThat(call.state()).isEqualTo(CallState.LISTENING);
        assertThat(call.currentTurnId()).isNotEqualTo(turn);
    }

    private static final class RecordingMediaConnection implements MediaConnection {
        final ArrayList<AudioFrame> sent=new ArrayList<>();int clears;int activations;UUID activeTurn=UUID.randomUUID();
        public Flux<byte[]> inboundAudio(){return Flux.just(new byte[]{0,0});}
        public boolean send(AudioFrame frame){if(!frame.turnId().equals(activeTurn))return false;sent.add(frame);return true;}
        public void activateTurn(UUID turnId){activeTurn=turnId;activations++;}
        public void clearBuffer(UUID nextTurnId){clears++;}
        public Mono<Void> close(){return Mono.empty();}
    }
}
