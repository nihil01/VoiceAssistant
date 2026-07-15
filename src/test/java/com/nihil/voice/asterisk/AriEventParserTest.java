package com.nihil.voice.asterisk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AriEventParserTest {
    private final AriEventParser parser = new AriEventParser();

    @Test
    void parsesTypedStasisEventAndRejectsMalformedInput() {
        var parsed = parser.parse("""
            {"type":"StasisStart","args":["media"],"channel":{"id":"ch-1","name":"WebSocket/x","caller":{"number":"123"},"dialplan":{"exten":"700"}}}
            """);
        assertThat(parsed).isPresent();
        assertThat(parsed.orElseThrow().type()).isEqualTo(AriEventType.STASIS_START);
        assertThat(parsed.orElseThrow().channel()).isEqualTo(new AriChannel("ch-1", "WebSocket/x", "123", "700"));
        assertThat(parser.parse("not-json")).isEmpty();
    }
}
