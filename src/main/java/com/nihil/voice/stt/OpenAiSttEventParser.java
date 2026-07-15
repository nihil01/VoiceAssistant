package com.nihil.voice.stt;

import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class OpenAiSttEventParser {
    private final ObjectMapper mapper;
    public OpenAiSttEventParser(ObjectMapper mapper) { this.mapper = mapper; }
    public Optional<SttEvent> parse(String raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            String type = text(root, "type");
            String eventId = text(root, "event_id");
            if (type == null) return Optional.empty();
            if (type.endsWith("transcription.delta")) return event(SttEvent.Type.PARTIAL, eventId, first(root, "delta", "text"), root);
            if (type.endsWith("transcription.completed") || type.endsWith("transcription.done")) return event(SttEvent.Type.FINAL, eventId, first(root, "transcript", "text"), root);
            if (type.endsWith("speech_started")) return Optional.of(new SttEvent(SttEvent.Type.SPEECH_STARTED, eventId, null, null, null));
            if (type.endsWith("speech_stopped")) return Optional.of(new SttEvent(SttEvent.Type.SPEECH_STOPPED, eventId, null, null, null));
            if ("error".equals(type) || type.endsWith(".error")) return Optional.of(new SttEvent(SttEvent.Type.ERROR, eventId, first(root.path("error"), "message", "code"), null, null));
            return Optional.empty();
        } catch (Exception ignored) { return Optional.empty(); }
    }
    private Optional<SttEvent> event(SttEvent.Type type, String id, String text, JsonNode root) {
        if (text == null || text.isBlank()) return Optional.empty();
        return Optional.of(new SttEvent(type, id, text, first(root, "language", "detected_language"), number(root, "confidence")));
    }
    private static String first(JsonNode node, String... names) { for (String name:names) { String value=text(node,name); if(value!=null&&!value.isBlank()) return value; } return null; }
    private static String text(JsonNode node,String name){JsonNode value=node.path(name);return value.isMissingNode()||value.isNull()?null:value.asText();}
    private static Double number(JsonNode node,String name){JsonNode value=node.path(name);return value.isNumber()?value.asDouble():null;}
}
