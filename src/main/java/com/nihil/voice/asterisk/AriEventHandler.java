package com.nihil.voice.asterisk;

import reactor.core.publisher.Mono;

public final class AriEventHandler {
    private final AsteriskBridgeService bridges;
    public AriEventHandler(AsteriskBridgeService bridges) { this.bridges = bridges; }

    public Mono<Void> handle(AriEvent event) {
        if (event.channel() == null || event.channel().id() == null) return Mono.empty();
        return switch (event.type()) {
            case STASIS_START -> {
                if (isMedia(event)) {
                    bridges.mediaEnteredStasis(event.channel().id());
                    yield Mono.empty();
                }
                if (isCaller(event.channel().name())) yield bridges.startCaller(event.channel()).then();
                yield Mono.empty();
            }
            case STASIS_END, CHANNEL_DESTROYED ->
                bridges.cleanupByChannel(event.channel().id(), event.type().name());
            default -> Mono.empty();
        };
    }

    private static boolean isMedia(AriEvent event) {
        String name = event.channel().name() == null ? "" : event.channel().name();
        return name.startsWith("WebSocket/") || name.startsWith("AudioSocket/") || name.startsWith("UnicastRTP/")
            || event.args().stream().anyMatch(arg -> arg.startsWith("media"));
    }
    private static boolean isCaller(String name) {
        return name != null && (name.startsWith("PJSIP/") || name.startsWith("SIP/") || name.startsWith("Local/"));
    }
}
