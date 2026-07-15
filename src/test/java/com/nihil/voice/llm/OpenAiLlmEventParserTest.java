package com.nihil.voice.llm;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OpenAiLlmEventParserTest {
    @Test void emitsOnlyTextDeltas() {
        var parser = new OpenAiLlmEventParser(new ObjectMapper());
        assertThat(parser.textDelta("{\"type\":\"response.output_text.delta\",\"delta\":\"Salam\"}")).contains("Salam");
        assertThat(parser.textDelta("{\"type\":\"response.completed\"}")).isEmpty();
        assertThat(parser.textDelta("[DONE]")).isEmpty();
    }
}
