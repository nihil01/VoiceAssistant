package com.nihil.voice.asterisk;

public final class AriException extends RuntimeException {
    public AriException(String message) { super(message); }
    public AriException(String message, Throwable cause) { super(message, cause); }
}
