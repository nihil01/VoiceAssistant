package com.nihil.voice.call;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class PlatformCallLifecycleObserverTest {
    @Test void rejectsMissingVoiceCoordinatorInsteadOfSilentlyDisablingPipeline() {
        assertThatThrownBy(() -> new PlatformCallLifecycleObserver(null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("VoiceCallCoordinator");
    }
}
