package com.nihil.voice.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AsteriskStartupValidatorTest {
    @Test void rejectsEnabledAsteriskWithoutAriCredentials() {
        var properties = properties("", "");
        assertThatThrownBy(() -> AsteriskStartupValidator.validate(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ARI_USERNAME")
            .hasMessageContaining("ARI_PASSWORD");
    }

    @Test void acceptsCompleteAriConfiguration() {
        assertThatCode(() -> AsteriskStartupValidator.validate(properties("voice-agent", "secret")))
            .doesNotThrowAnyException();
    }

    private static AsteriskProperties properties(String username, String password) {
        return new AsteriskProperties(true, URI.create("http://asterisk:8088"), username, password,
            "ai-agent", "slin16", "both", URI.create("ws://asterisk:8088/media"),
            Duration.ofSeconds(5), Duration.ofSeconds(3), 256, 256);
    }
}
