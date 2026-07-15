package com.nihil.voice.stt;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OpenAiSttEventParserTest {
    private final OpenAiSttEventParser parser = new OpenAiSttEventParser(new ObjectMapper());
    @Test void parsesPartialFinalAndSpeechEventsDefensively() {
        assertThat(parser.parse("{\"type\":\"conversation.item.input_audio_transcription.delta\",\"delta\":\"Sal\",\"event_id\":\"e1\"}").orElseThrow().type()).isEqualTo(SttEvent.Type.PARTIAL);
        var completed = parser.parse("{\"type\":\"conversation.item.input_audio_transcription.completed\",\"transcript\":\"Salam\",\"event_id\":\"e2\"}").orElseThrow();
        assertThat(completed.type()).isEqualTo(SttEvent.Type.FINAL);
        assertThat(completed.text()).isEqualTo("Salam");
        assertThat(parser.parse("{\"type\":\"input_audio_buffer.speech_started\",\"event_id\":\"e3\"}").orElseThrow().type()).isEqualTo(SttEvent.Type.SPEECH_STARTED);
    }
}
