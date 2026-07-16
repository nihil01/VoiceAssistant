package com.nihil.voice.audio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PcmFramePacketizerTest {
    private static final int FRAME_BYTES = 640; // 20 ms, PCM16 mono, 16 kHz

    @Test
    void buffersTheStartupWindowThenEmitsFixedTwentyMillisecondFrames() {
        var packetizer = new PcmFramePacketizer(16_000, 20, 3);
        var output = new ArrayList<byte[]>();

        output.addAll(packetizer.append(new byte[FRAME_BYTES]));
        output.addAll(packetizer.append(new byte[FRAME_BYTES]));

        assertThat(output).isEmpty();

        output.addAll(packetizer.append(new byte[FRAME_BYTES + 10]));

        assertThat(output).hasSize(3);
        assertThat(output).allSatisfy(frame -> assertThat(frame).hasSize(FRAME_BYTES));

        output.addAll(packetizer.append(new byte[FRAME_BYTES - 10]));

        assertThat(output).hasSize(4);
        assertThat(output.get(3)).hasSize(FRAME_BYTES);
        assertThat(packetizer.finish()).isEmpty();
    }

    @Test
    void flushesShortAudioWithoutPaddingOrDataLoss() {
        var packetizer = new PcmFramePacketizer(16_000, 20, 6);
        byte[] shortAudio = new byte[321];
        for (int index = 0; index < shortAudio.length; index++) {
            shortAudio[index] = (byte) index;
        }

        assertThat(packetizer.append(shortAudio)).isEmpty();

        List<byte[]> remaining = packetizer.finish();

        assertThat(remaining).hasSize(1);
        assertThat(remaining.getFirst()).containsExactly(shortAudio);
    }
}
