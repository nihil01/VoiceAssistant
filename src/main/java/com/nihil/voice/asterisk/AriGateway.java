package com.nihil.voice.asterisk;

import reactor.core.publisher.Mono;

public interface AriGateway {
    Mono<Void> answer(String channelId);
    Mono<String> createBridge(String bridgeId, String name);
    Mono<Void> addChannel(String bridgeId, String channelId);
    Mono<AriChannel> createExternalMedia(String channelId, String callId);
    Mono<String> channelVariable(String channelId, String variable);
    Mono<Void> deleteChannel(String channelId);
    Mono<Void> deleteBridge(String bridgeId);
}
