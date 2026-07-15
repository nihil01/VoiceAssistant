package com.nihil.voice.call;

import com.nihil.voice.asterisk.MediaConnection;
import com.nihil.voice.conversation.ConversationService;
import com.nihil.voice.conversation.TurnCancellationRegistry;
import com.nihil.voice.stt.SttClient;
import com.nihil.voice.stt.SttEvent;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class VoiceCallCoordinator {
    private final SttClient stt; private final ConversationService conversation; private final TurnCancellationRegistry cancellations;
    public VoiceCallCoordinator(SttClient stt,ConversationService conversation,TurnCancellationRegistry cancellations){this.stt=stt;this.conversation=conversation;this.cancellations=cancellations;}
    public Mono<Void> run(CallSession call,MediaConnection media){
        return stt.transcribe(media.inboundAudio()).concatMap(event->handle(call,media,event),1).then();
    }
    private Mono<Void> handle(CallSession call,MediaConnection media,SttEvent event){
        return switch(event.type()){
            case FINAL -> conversation.respond(call,event.text(),event.eventId())
                .handle((frame,sink)->{if(media.send(frame))sink.next(frame);else sink.error(new IllegalStateException("Asterisk media output queue rejected audio"));}).then();
            case SPEECH_STARTED -> interruptIfSpeaking(call,media);
            case ERROR -> Mono.error(new IllegalStateException("STT provider error: "+event.text()));
            default -> Mono.empty();
        };
    }
    private Mono<Void> interruptIfSpeaking(CallSession call,MediaConnection media){
        if(call.state()!=CallState.SPEAKING)return Mono.empty();
        UUID old=call.currentTurnId();cancellations.cancel(call.internalCallId(),old);
        UUID replacement=call.interruptAndStartListening();media.clearBuffer(replacement);return Mono.empty();
    }
}
