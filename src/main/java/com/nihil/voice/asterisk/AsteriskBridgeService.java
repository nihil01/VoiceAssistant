package com.nihil.voice.asterisk;

import com.nihil.voice.call.CallSession;
import com.nihil.voice.call.CallSessionManager;
import com.nihil.voice.call.CallState;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/** Owns the strict ARI bridge setup and teardown ordering for a call. */
public final class AsteriskBridgeService {
    public static final String CONNECTION_VARIABLE = "MEDIA_WEBSOCKET_CONNECTION_ID";
    private final AriGateway ari;
    private final MediaGateway media;
    private final CallSessionManager calls;
    private final com.nihil.voice.call.CallLifecycleObserver lifecycle;
    private final Duration mediaReadyTimeout;
    private final ConcurrentMap<String, Sinks.One<Void>> mediaReady = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> mediaReadinessKeys = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, MediaConnection> connections = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, AtomicBoolean> cleanup = new ConcurrentHashMap<>();

    public AsteriskBridgeService(AriGateway ari, MediaGateway media, CallSessionManager calls) {
        this(ari, media, calls, com.nihil.voice.call.CallLifecycleObserver.NOOP, Duration.ofSeconds(10));
    }
    public AsteriskBridgeService(AriGateway ari, MediaGateway media, CallSessionManager calls, com.nihil.voice.call.CallLifecycleObserver lifecycle) {
        this(ari, media, calls, lifecycle, Duration.ofSeconds(10));
    }
    public AsteriskBridgeService(AriGateway ari, MediaGateway media, CallSessionManager calls,
                                 com.nihil.voice.call.CallLifecycleObserver lifecycle, Duration mediaReadyTimeout) {
        this.ari = ari; this.media = media; this.calls = calls; this.lifecycle = lifecycle;
        this.mediaReadyTimeout = mediaReadyTimeout;
    }

    public Mono<CallSession> startCaller(AriChannel caller) {
        return Mono.defer(() -> {
            CallSession call = calls.create(caller.id(), caller.callerNumber(), caller.destinationNumber());
            String bridgeId = "ai-" + UUID.randomUUID();
            String requestedMediaId = "media-" + UUID.randomUUID();
            Sinks.One<Void> ready = Sinks.one();
            mediaReady.put(requestedMediaId, ready);
            mediaReadinessKeys.put(call.internalCallId(), requestedMediaId);
            return Mono.defer(() -> ari.answer(caller.id()))
                .then(Mono.fromRunnable(() -> call.transitionTo(CallState.ANSWERED)))
                .then(Mono.defer(() -> ari.createBridge(bridgeId, "ai-agent-" + call.internalCallId())))
                .doOnNext(created -> calls.bindBridge(call.internalCallId(), created))
                .flatMap(created -> Mono.defer(() -> ari.addChannel(created, caller.id())).thenReturn(created))
                .then(Mono.defer(() -> ari.createExternalMedia(requestedMediaId, call.internalCallId().toString())))
                .flatMap(external -> {
                    if (!requestedMediaId.equals(external.id())) {
                        mediaReady.remove(requestedMediaId, ready);
                        mediaReady.put(external.id(), ready);
                        mediaReadinessKeys.put(call.internalCallId(), external.id());
                    }
                    return Mono.defer(() -> ari.channelVariable(external.id(), CONNECTION_VARIABLE))
                        .doOnNext(connectionId -> calls.bindMedia(call.internalCallId(), external.id(), connectionId));
                })
                .then(Mono.defer(() -> media.connect(call)))
                .doOnNext(connection -> connections.put(call.internalCallId(), connection))
                .then(ready.asMono().timeout(mediaReadyTimeout))
                .then(Mono.defer(() -> ari.addChannel(call.bridgeId(), call.mediaChannelId())))
                .then(Mono.defer(() -> {
                    call.transitionTo(CallState.LISTENING);
                    mediaReady.remove(call.mediaChannelId(), ready);
                    mediaReadinessKeys.remove(call.internalCallId());
                    return lifecycle.onReady(call, connections.get(call.internalCallId())).thenReturn(call);
                }))
                .onErrorResume(error -> cleanup(call, "setup_failed").then(Mono.error(error)));
        });
    }

    public void mediaEnteredStasis(String channelId) {
        Sinks.One<Void> ready = mediaReady.get(channelId);
        if (ready != null) ready.tryEmitEmpty();
    }

    public Mono<Void> cleanupByChannel(String channelId, String reason) {
        return Mono.defer(() -> calls.findByChannel(channelId).map(call -> cleanup(call, reason)).orElseGet(Mono::empty));
    }

    private Mono<Void> cleanup(CallSession call, String reason) {
        AtomicBoolean guard = cleanup.computeIfAbsent(call.internalCallId(), ignored -> new AtomicBoolean());
        if (!guard.compareAndSet(false, true)) return Mono.empty();
        if (!call.state().terminal() && call.state() != CallState.ENDING) call.transitionTo(CallState.ENDING);
        String readinessKey = mediaReadinessKeys.remove(call.internalCallId());
        if (readinessKey != null) {
            mediaReady.computeIfPresent(readinessKey, (id, ready) -> {
                ready.tryEmitError(new AriException("Call ended during media setup: " + reason));
                return null;
            });
        }
        MediaConnection connection = connections.remove(call.internalCallId());
        Mono<Void> closeMedia = connection == null ? Mono.empty() : connection.close().onErrorResume(error -> Mono.empty());
        Mono<Void> deleteMedia = call.mediaChannelId() == null ? Mono.empty()
            : ari.deleteChannel(call.mediaChannelId()).onErrorResume(error -> Mono.empty());
        Mono<Void> deleteBridge = call.bridgeId() == null ? Mono.empty()
            : ari.deleteBridge(call.bridgeId()).onErrorResume(error -> Mono.empty());
        return lifecycle.onEnding(call, reason)
            .materialize()
            .flatMap(lifecycleSignal -> closeMedia
                .then(deleteMedia)
                .then(deleteBridge)
                .then(Mono.defer(() -> lifecycleSignal.isOnError()
                    ? Mono.<Void>error(lifecycleSignal.getThrowable())
                    : Mono.empty())))
            .doFinally(ignored -> {
                if (call.state() == CallState.ENDING) call.transitionTo(CallState.ENDED);
                calls.remove(call.internalCallId());
                cleanup.remove(call.internalCallId(), guard);
            });
    }

    public MediaConnection connection(CallSession call) { return connections.get(call.internalCallId()); }
}
