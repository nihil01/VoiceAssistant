package com.nihil.voice.asterisk;

import com.nihil.voice.audio.AudioFrame;
import com.nihil.voice.call.CallSession;
import com.nihil.voice.config.AsteriskProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Connects to Asterisk chan_websocket's server-mode /media/{connectionId}. */
public final class ReactorNettyMediaGateway implements MediaGateway {
    private static final Logger log = LoggerFactory.getLogger(ReactorNettyMediaGateway.class);
    private final WebSocketClient client;
    private final AsteriskProperties properties;
    private final MeterRegistry meters;

    public ReactorNettyMediaGateway(AsteriskProperties properties, MeterRegistry meters) {
        this(new ReactorNettyWebSocketClient(), properties, meters);
    }
    ReactorNettyMediaGateway(WebSocketClient client, AsteriskProperties properties, MeterRegistry meters) {
        this.client = client;this.properties = properties; this.meters = meters;
    }

    public Mono<MediaConnection> connect(CallSession call) {
        return Mono.create(result -> {
            log.info("Connecting Asterisk media WebSocket callId={} connectionId={}",call.internalCallId(),call.mediaConnectionId());
            var channel = new MediaChannel(properties.inboundBufferFrames(), meters);
            UUID initialTurn = call.currentTurnId() == null ? call.startTurn() : call.currentTurnId();
            channel.activate(initialTurn);
            var holder = new Disposable[1];
            AtomicLong frames = new AtomicLong();
            AtomicBoolean closed = new AtomicBoolean();
            MediaConnection connection = new MediaConnection() {
                public Flux<byte[]> inboundAudio() { return channel.inboundAudio(); }
                public boolean send(AudioFrame frame) { return channel.offer(frame.turnId(), frame.payload()); }
                public void clearBuffer(UUID nextTurnId) { channel.clearBuffer(nextTurnId); }
                public Mono<Void> close() { return Mono.fromRunnable(() -> {
                    if (closed.compareAndSet(false, true)) {
                        channel.complete();
                        if (holder[0] != null) holder[0].dispose();
                    }
                }); }
            };

            Mono<Void> socket = client.execute(properties.mediaUri(call.mediaConnectionId()), session -> {
                log.info("Asterisk media WebSocket connected callId={} channelId={}",call.internalCallId(),call.mediaChannelId());
                result.success(connection);
                Mono<Void> receive = session.receive().doOnNext(message -> {
                    if (message.getType() == WebSocketMessage.Type.BINARY) {
                        DataBuffer buffer = message.getPayload();
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        long frameNumber=frames.incrementAndGet();
                        if(frameNumber==1)log.info("First binary media frame received callId={} bytes={}",call.internalCallId(),bytes.length);
                        channel.acceptBinary(bytes);
                    } else if (message.getType() == WebSocketMessage.Type.TEXT) {
                        channel.acceptControl(message.getPayloadAsText());
                    }
                }).then();
                Mono<Void> send = session.send(channel.outboundPayloads().map(payload -> payload.binary()
                    ? session.binaryMessage(factory -> factory.wrap(payload.bytes()))
                    : session.textMessage(payload.text())));
                Mono<Void> warning = Mono.delay(properties.noFrameTimeout()).filter(ignored -> frames.get() == 0)
                    .doOnNext(ignored -> {
                        meters.counter("asterisk.media.no_frames").increment();
                        log.warn("No binary media frames received callId={} channelId={} timeout={}",
                            call.internalCallId(), call.mediaChannelId(), properties.noFrameTimeout());
                    }).then();
                return Mono.when(receive, send, warning);
            }).doFinally(signal -> {
                channel.complete();
                if (!closed.get()) {
                    meters.counter("asterisk.media.disconnects").increment();
                }
            });
            holder[0] = socket.subscribe(null, result::error);
            result.onCancel(() -> holder[0].dispose());
        });
    }
}
