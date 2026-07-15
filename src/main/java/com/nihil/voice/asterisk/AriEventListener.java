package com.nihil.voice.asterisk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

public final class AriEventListener {
    private static final Logger log = LoggerFactory.getLogger(AriEventListener.class);
    private final AriEventStream stream;
    private final AriEventHandler handler;
    private volatile Disposable subscription;

    public AriEventListener(AriEventStream stream, AriEventHandler handler) {
        this.stream = stream; this.handler = handler;
    }
    public synchronized void start() {
        if (subscription == null || subscription.isDisposed()) {
            log.info("Starting ARI event listener");
            // flatMap is intentional: caller setup waits for a later media StasisStart event.
            subscription = stream.events()
                .doOnNext(event -> log.info("ARI event type={} channelId={}",
                    event.type(), event.channel() == null ? null : event.channel().id()))
                .flatMap(handler::handle, 64)
                .subscribe(ignored -> { }, error -> log.error("ARI event listener terminated", error));
        }
    }
    public synchronized void stop() {
        if (subscription != null) subscription.dispose();
        subscription = null;
    }
}
