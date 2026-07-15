package com.nihil.voice.audio;

import java.util.Objects;
import java.util.UUID;

public record AudioFrame(UUID turnId, byte[] payload) {
    public AudioFrame {
        Objects.requireNonNull(turnId);
        Objects.requireNonNull(payload);
        payload = payload.clone();
    }
    @Override public byte[] payload() { return payload.clone(); }
}
