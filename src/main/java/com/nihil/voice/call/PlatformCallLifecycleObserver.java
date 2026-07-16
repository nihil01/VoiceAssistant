package com.nihil.voice.call;

import com.nihil.voice.asterisk.MediaConnection;
import com.nihil.voice.persistence.ReactiveCallStore;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

public final class PlatformCallLifecycleObserver implements CallLifecycleObserver {
    private static final Logger log = LoggerFactory.getLogger(
            PlatformCallLifecycleObserver.class
    );

    private final VoiceCallCoordinator coordinator;
    private final ReactiveCallStore store;
    private final UUID tenantId;
    private final UUID assistantId;
    private final ConcurrentMap<UUID, Disposable> active = new ConcurrentHashMap<>();
    private final Set<UUID> persistedCalls = ConcurrentHashMap.newKeySet();

    public PlatformCallLifecycleObserver(
            VoiceCallCoordinator coordinator,
            ReactiveCallStore store,
            UUID tenantId,
            UUID assistantId
    ) {
        if (coordinator == null) {
            throw new IllegalArgumentException(
                    "VoiceCallCoordinator is required when the voice pipeline is enabled"
            );
        }
        this.coordinator = coordinator;
        this.store = Objects.requireNonNull(store, "ReactiveCallStore");
        this.tenantId = tenantId;
        this.assistantId = assistantId;
    }

    @Override
    public Mono<Void> onReady(CallSession call, MediaConnection media) {
        log.info(
                "Call ready; persisting and starting voice pipeline callId={} mediaChannelId={}",
                call.internalCallId(),
                call.mediaChannelId()
        );
        return store.create(call, tenantId, assistantId)
                .then(Mono.fromRunnable(() -> startPipeline(call, media)));
    }

    @Override
    public Mono<Void> onEnding(CallSession call, String reason) {
        return Mono.fromRunnable(() -> stopPipeline(call))
                .then(Mono.defer(() -> {
                    if (!persistedCalls.remove(call.internalCallId())) {
                        log.info(
                                "Skipping terminal persistence for call that never became ready callId={} reason={}",
                                call.internalCallId(),
                                reason
                        );
                        return Mono.empty();
                    }
                    return store.end(
                                    call.internalCallId(),
                                    terminalStatus(reason),
                                    reason,
                                    Instant.now()
                            )
                            .then(store.enqueuePostCall(call.internalCallId()));
                }));
    }

    private void startPipeline(CallSession call, MediaConnection media) {
        persistedCalls.add(call.internalCallId());
        var subscriptionReference = new AtomicReference<Disposable>();
        Disposable subscription = coordinator.run(call, media)
                .doOnSubscribe(ignored -> log.info(
                        "Voice STT pipeline started callId={}",
                        call.internalCallId()
                ))
                .doFinally(ignored -> active.remove(
                        call.internalCallId(),
                        subscriptionReference.get()
                ))
                .subscribe(
                        null,
                        error -> log.error(
                                "Voice pipeline failed callId={}",
                                call.internalCallId(),
                                error
                        )
                );
        subscriptionReference.set(subscription);
        Disposable old = active.put(call.internalCallId(), subscription);
        if (old != null) {
            old.dispose();
        }
        if (subscription.isDisposed()) {
            active.remove(call.internalCallId(), subscription);
        }
    }

    private void stopPipeline(CallSession call) {
        Disposable subscription = active.remove(call.internalCallId());
        if (subscription != null) {
            subscription.dispose();
        }
        coordinator.closeCall(call.internalCallId());
    }

    private static String terminalStatus(String reason) {
        if (reason != null) {
            String normalized = reason.toLowerCase();
            if (normalized.contains("fail") || normalized.contains("error")) {
                return CallState.FAILED.name();
            }
        }
        return CallState.ENDED.name();
    }
}
