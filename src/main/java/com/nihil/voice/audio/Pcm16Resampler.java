package com.nihil.voice.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class Pcm16Resampler {
    private Pcm16Resampler() {}
    public static byte[] resampleMono(byte[] input, int sourceRate, int targetRate) {
        if (input == null || input.length == 0) return new byte[0];
        if ((input.length & 1) != 0) throw new IllegalArgumentException("PCM16 byte count must be even");
        if (sourceRate <= 0 || targetRate <= 0) throw new IllegalArgumentException("Sample rates must be positive");
        if (sourceRate == targetRate) return input.clone();
        int sourceSamples = input.length / 2;
        int targetSamples = Math.max(1, (int)Math.round(sourceSamples * (double)targetRate / sourceRate));
        ByteBuffer source = ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN);
        short[] samples = new short[sourceSamples];
        for (int i = 0; i < sourceSamples; i++) samples[i] = source.getShort();
        ByteBuffer output = ByteBuffer.allocate(targetSamples * 2).order(ByteOrder.LITTLE_ENDIAN);
        double ratio = (double)sourceRate / targetRate;
        for (int i = 0; i < targetSamples; i++) {
            double position = i * ratio;
            int left = Math.min((int)position, sourceSamples - 1);
            int right = Math.min(left + 1, sourceSamples - 1);
            double fraction = position - left;
            int value = (int)Math.round(samples[left] + (samples[right] - samples[left]) * fraction);
            output.putShort((short) Math.clamp(value, Short.MIN_VALUE, Short.MAX_VALUE));
        }
        return output.array();
    }
}
