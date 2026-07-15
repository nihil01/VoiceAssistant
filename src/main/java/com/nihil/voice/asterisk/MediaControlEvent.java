package com.nihil.voice.asterisk;

import java.util.Map;

public record MediaControlEvent(MediaControlCommand command, Map<String, String> attributes) {}
