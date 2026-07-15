package com.nihil.voice.call;
import com.nihil.voice.llm.ConversationMessage;
import java.util.UUID;
import reactor.core.publisher.Mono;
@FunctionalInterface public interface ConversationMessageSink {
 ConversationMessageSink NOOP=(call,role,text,eventId,turnId)->Mono.empty();
 Mono<Void> record(CallSession call, ConversationMessage.Role role, String text, String providerEventId, UUID turnId);
}
