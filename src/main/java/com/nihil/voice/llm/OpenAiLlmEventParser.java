package com.nihil.voice.llm;

import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class OpenAiLlmEventParser {
    private final ObjectMapper mapper;
    public OpenAiLlmEventParser(ObjectMapper mapper) { this.mapper=mapper; }
    public Optional<String> textDelta(String raw) {
        if (raw == null) return Optional.empty();
        String payload = raw.strip();
        if (payload.startsWith("data:")) payload = payload.substring(5).strip();
        if (payload.isBlank() || "[DONE]".equals(payload)) return Optional.empty();
        try {
            JsonNode root=mapper.readTree(payload);
            if (!"response.output_text.delta".equals(root.path("type").asText())) return Optional.empty();
            String delta=root.path("delta").asText();
            return delta.isBlank()?Optional.empty():Optional.of(delta);
        } catch(Exception ignored){return Optional.empty();}
    }
}
