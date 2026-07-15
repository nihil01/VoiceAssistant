package com.nihil.voice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AsteriskPropertiesTest {
    @Test
    void derivesSafeAriAndMediaUris() {
        var properties = new AsteriskProperties(true, URI.create("http://asterisk:8088"), "user", "secret", "agent app",
            "slin16", "both", URI.create("ws://asterisk:8088/media"), Duration.ofSeconds(4), Duration.ofSeconds(3), 64, 128);
        assertThat(properties.eventsUri().toString()).isEqualTo("ws://asterisk:8088/ari/events?app=agent%20app&subscribeAll=false");
        assertThat(properties.mediaUri("abc/def").toString()).isEqualTo("ws://asterisk:8088/media/abc%2Fdef");
    }
}
