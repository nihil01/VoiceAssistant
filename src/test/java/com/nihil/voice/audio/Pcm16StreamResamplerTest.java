package com.nihil.voice.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class Pcm16StreamResamplerTest {
    @Test void preservesSamplesAcrossOddHttpChunkBoundaries() {
        ByteBuffer pcm=ByteBuffer.allocate(200).order(ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<100;i++)pcm.putShort((short)(i*200-9000));
        byte[] source=pcm.array();
        byte[] expected=Pcm16Resampler.resampleMono(source,24000,16000);
        var streaming=new Pcm16StreamResampler(24000,16000);
        var output=new ByteArrayOutputStream();
        output.writeBytes(streaming.append(java.util.Arrays.copyOfRange(source,0,3)));
        output.writeBytes(streaming.append(java.util.Arrays.copyOfRange(source,3,20)));
        output.writeBytes(streaming.append(java.util.Arrays.copyOfRange(source,20,113)));
        output.writeBytes(streaming.append(java.util.Arrays.copyOfRange(source,113,source.length)));
        output.writeBytes(streaming.finish());
        assertThat(output.toByteArray()).containsExactly(expected);
    }

    @Test void rejectsTruncatedFinalPcmSample() {
        var streaming=new Pcm16StreamResampler(24000,16000);
        streaming.append(new byte[]{1});
        assertThatThrownBy(streaming::finish).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("incomplete PCM16 sample");
    }
}
