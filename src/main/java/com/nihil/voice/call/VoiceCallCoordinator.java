package com.nihil.voice.call;

import com.nihil.voice.asterisk.MediaConnection;
import com.nihil.voice.conversation.ConversationService;
import com.nihil.voice.conversation.TurnCancellationRegistry;
import com.nihil.voice.stt.SttClient;
import com.nihil.voice.stt.SttEvent;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public final class VoiceCallCoordinator {
    private static final Logger log=LoggerFactory.getLogger(VoiceCallCoordinator.class);
    private final SttClient stt; private final ConversationService conversation; private final TurnCancellationRegistry cancellations;
    private final boolean logTranscripts;
    public VoiceCallCoordinator(SttClient stt,ConversationService conversation,TurnCancellationRegistry cancellations){this(stt,conversation,cancellations,false);}
    public VoiceCallCoordinator(SttClient stt,ConversationService conversation,TurnCancellationRegistry cancellations,boolean logTranscripts){this.stt=stt;this.conversation=conversation;this.cancellations=cancellations;this.logTranscripts=logTranscripts;}
    public Mono<Void> run(CallSession call,MediaConnection media){
        return stt.transcribe(media.inboundAudio()).concatMap(event->handle(call,media,event),1).then();
    }
    private Mono<Void> handle(CallSession call,MediaConnection media,SttEvent event){
        return switch(event.type()){
            case PARTIAL -> { logTranscript("PARTIAL",call,event); yield Mono.empty(); }
            case FINAL -> { logTranscript("FINAL",call,event); yield conversation.respond(call,event.text(),event.eventId())
                .doOnSubscribe(ignored->{
                    UUID turnId=call.currentTurnId();
                    if(turnId==null)throw new IllegalStateException("Conversation started without an active turn");
                    media.activateTurn(turnId);
                    log.info("Activated Asterisk media output turn callId={} turnId={}",call.internalCallId(),turnId);
                })
                .handle((frame,sink)->{if(media.send(frame))sink.next(frame);else sink.error(new IllegalStateException("Asterisk media output queue rejected audio"));}).then(); }
            case SPEECH_STARTED -> interruptIfSpeaking(call,media);
            case ERROR -> Mono.error(new IllegalStateException("STT provider error: "+event.text()));
            default -> Mono.empty();
        };
    }
    private void logTranscript(String type,CallSession call,SttEvent event){
        if(!logTranscripts)return;
        String text=event.text()==null?"":event.text().replace('\n',' ').replace('\r',' ');
        if(text.length()>500)text=text.substring(0,500)+"…";
        log.info("STT {} callId={} eventId={} text={}",type,call.internalCallId(),event.eventId(),text);
    }
    private Mono<Void> interruptIfSpeaking(CallSession call,MediaConnection media){
        if(call.state()!=CallState.SPEAKING)return Mono.empty();
        UUID old=call.currentTurnId();cancellations.cancel(call.internalCallId(),old);
        UUID replacement=call.interruptAndStartListening();media.clearBuffer(replacement);return Mono.empty();
    }
}
