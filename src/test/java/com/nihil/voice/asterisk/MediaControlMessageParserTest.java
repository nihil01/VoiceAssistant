package com.nihil.voice.asterisk;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MediaControlMessageParserTest {
    private final MediaControlMessageParser parser = new MediaControlMessageParser();

    @Test
    void parsesPlainMediaStart() {
        var event = parser.parse("MEDIA_START connection_id:abc channel_id:media-1 format:slin16 optimal_frame_size:640 ptime:20");
        assertThat(event.command()).isEqualTo(MediaControlCommand.MEDIA_START);
        assertThat(event.attributes()).containsEntry("connection_id", "abc").containsEntry("ptime", "20");
    }

    @Test
    void parsesJsonFlowControl() {
        var event = parser.parse("{\"event\":\"MEDIA_XOFF\",\"connection_id\":\"abc\"}");
        assertThat(event.command()).isEqualTo(MediaControlCommand.MEDIA_XOFF);
        assertThat(event.attributes()).containsEntry("connection_id", "abc");
    }
}
