package com.nihil.voice.audio;

import static org.assertj.core.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class Pcm16ResamplerTest {
    @Test void convertsTwentyFourKhzPcmToSixteenKhzWithoutChangingEndianOrChannels() {
        ByteBuffer input = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);
        input.putShort((short)0).putShort((short)1200).putShort((short)2400);
        byte[] output = Pcm16Resampler.resampleMono(input.array(), 24000, 16000);
        assertThat(output).hasSize(4);
        ByteBuffer result = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(result.getShort()).isEqualTo((short)0);
        assertThat(result.getShort()).isBetween((short)1700, (short)1900);
    }
}
