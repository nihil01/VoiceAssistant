package com.nihil.voice.asterisk;

import java.util.List;

public record AriEvent(AriEventType type, AriChannel channel, List<String> args, String digit) {
    public AriEvent { args = args == null ? List.of() : List.copyOf(args); }
}
