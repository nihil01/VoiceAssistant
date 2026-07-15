package com.nihil.voice.asterisk;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class MediaControlMessageParser {
    private final ObjectMapper mapper;
    public MediaControlMessageParser() { this(new ObjectMapper()); }
    public MediaControlMessageParser(ObjectMapper mapper) { this.mapper = mapper; }

    public MediaControlEvent parse(String raw) {
        if (raw == null || raw.isBlank()) return new MediaControlEvent(MediaControlCommand.UNKNOWN, Map.of());
        String value = raw.trim();
        try {
            if (value.startsWith("{")) {
                Map<String, Object> json = mapper.readValue(value, new TypeReference<>() {});
                String name = String.valueOf(json.getOrDefault("event", json.getOrDefault("command", "UNKNOWN")));
                var attributes = new LinkedHashMap<String, String>();
                json.forEach((key, item) -> { if (item != null && !key.equals("event") && !key.equals("command")) attributes.put(key, String.valueOf(item)); });
                return new MediaControlEvent(command(name), Map.copyOf(attributes));
            }
        } catch (Exception ignored) {
            return new MediaControlEvent(MediaControlCommand.UNKNOWN, Map.of("raw", value));
        }
        String[] parts = value.split("\s+");
        var attributes = new LinkedHashMap<String, String>();
        for (int i = 1; i < parts.length; i++) {
            int separator = parts[i].indexOf(':');
            if (separator > 0) attributes.put(parts[i].substring(0, separator), parts[i].substring(separator + 1));
        }
        return new MediaControlEvent(command(parts[0]), Map.copyOf(attributes));
    }

    private MediaControlCommand command(String value) {
        try { return MediaControlCommand.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return MediaControlCommand.UNKNOWN; }
    }
}
