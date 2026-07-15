package com.nihil.voice.asterisk;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class AriEventParser {
    private final ObjectMapper mapper;
    public AriEventParser() { this(new ObjectMapper()); }
    public AriEventParser(ObjectMapper mapper) { this.mapper = mapper; }

    public Optional<AriEvent> parse(String raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            JsonNode node = root.path("channel");
            if (!root.isObject() || !node.isObject()) return Optional.empty();
            AriChannel channel = new AriChannel(text(node, "id"), text(node, "name"),
                text(node.path("caller"), "number"), text(node.path("dialplan"), "exten"));
            List<String> args = root.path("args").isArray()
                ? root.path("args").valueStream().map(JsonNode::asText).toList() : List.of();
            return Optional.of(new AriEvent(type(text(root, "type")), channel, args, text(root, "digit")));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static AriEventType type(String value) {
        if (value == null) return AriEventType.UNKNOWN;
        String normalized = value.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
        try { return AriEventType.valueOf(normalized); }
        catch (IllegalArgumentException ignored) { return AriEventType.UNKNOWN; }
    }
}
