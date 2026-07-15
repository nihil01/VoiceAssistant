package com.nihil.voice.call;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CallSessionManagerTest {
    @Test
    void indexesAndRemovesAllAsteriskIdentifiersIdempotently() {
        var manager = new CallSessionManager();
        var call = manager.create("caller-1", "+994500000000", "700");
        manager.bindBridge(call.internalCallId(), "bridge-1");
        manager.bindMedia(call.internalCallId(), "media-1", "connection-1");
        assertThat(manager.findByChannel("caller-1")).containsSame(call);
        assertThat(manager.findByChannel("media-1")).containsSame(call);
        assertThat(manager.findByConnection("connection-1")).containsSame(call);
        assertThat(manager.remove(call.internalCallId())).containsSame(call);
        assertThat(manager.remove(call.internalCallId())).isEmpty();
        assertThat(manager.activeCount()).isZero();
    }
}
