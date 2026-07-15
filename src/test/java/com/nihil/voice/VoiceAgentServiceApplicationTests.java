package com.nihil.voice;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class VoiceAgentServiceApplicationTests {
    @Test
    void exposesApplicationEntryPoint() {
        assertThat(VoiceAgentServiceApplication.class).isNotNull();
    }
}
