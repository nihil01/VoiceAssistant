package com.nihil.voice.persistence;

import com.nihil.voice.call.CallSession;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface ReactiveCallStore {
    Mono<Void> create(CallSession call, UUID tenantId, UUID assistantId);
    Mono<Void> appendMessage(CallMessageData message);
    Mono<Void> end(UUID callId, String status, String hangupCause, Instant endedAt);
    Mono<Void> enqueueCrmSync(UUID callId, UUID tenantId, String externalCallId);
    Mono<Void> enqueuePostCall(UUID callId);
}
