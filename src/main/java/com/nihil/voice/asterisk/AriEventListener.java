package com.nihil.voice.asterisk;

import reactor.core.Disposable;

public final class AriEventListener {
    private final AriEventStream stream;
    private final AriEventHandler handler;
    private volatile Disposable subscription;

    public AriEventListener(AriEventStream stream, AriEventHandler handler) {
        this.stream = stream; this.handler = handler;
    }
    public synchronized void start() {
        if (subscription == null || subscription.isDisposed()) {
            // flatMap is intentional: caller setup waits for a later media StasisStart event.
            subscription = stream.events().flatMap(handler::handle, 64).subscribe();
        }
    }
    public synchronized void stop() {
        if (subscription != null) subscription.dispose();
        subscription = null;
    }
}
