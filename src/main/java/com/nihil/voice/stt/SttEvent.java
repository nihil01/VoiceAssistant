package com.nihil.voice.stt;

public record SttEvent(Type type, String eventId, String text, String language, Double confidence) {
    public enum Type { PARTIAL, FINAL, SPEECH_STARTED, SPEECH_STOPPED, ERROR, CLOSED }
}
