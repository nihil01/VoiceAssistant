package com.nihil.voice.call;

public enum CallState {
    CREATED, RINGING, ANSWERED, LISTENING, THINKING, SPEAKING, INTERRUPTING, ENDING, ENDED, FAILED;

    public boolean terminal() {
        return this == ENDED || this == FAILED;
    }
}
