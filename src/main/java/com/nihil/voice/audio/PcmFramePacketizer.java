package com.nihil.voice.audio;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts arbitrary PCM16 mono transport chunks into fixed-duration audio frames.
 *
 * <p>The first few frames are intentionally buffered. That small playout window prevents
 * Asterisk from underrunning while the HTTP TTS stream is still warming up.</p>
 */
public final class PcmFramePacketizer {
    private static final int PCM16_BYTES_PER_SAMPLE = 2;

    private final int frameBytes;
    private final int startupFrames;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    private boolean started;
    private boolean finished;

    public PcmFramePacketizer(int sampleRate, int frameDurationMillis, int startupFrames) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be positive");
        }
        if (frameDurationMillis <= 0) {
            throw new IllegalArgumentException("frameDurationMillis must be positive");
        }
        if (startupFrames < 1) {
            throw new IllegalArgumentException("startupFrames must be positive");
        }

        long bytes = (long) sampleRate * PCM16_BYTES_PER_SAMPLE * frameDurationMillis / 1_000;
        if (bytes <= 0 || bytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("frame size is out of range");
        }

        this.frameBytes = (int) bytes;
        this.startupFrames = startupFrames;
    }

    public synchronized List<byte[]> append(byte[] chunk) {
        ensureOpen();
        if (chunk == null || chunk.length == 0) {
            return List.of();
        }

        pending.writeBytes(chunk);
        if (!started && pending.size() < frameBytes * startupFrames) {
            return List.of();
        }

        started = true;
        return drainCompleteFrames();
    }

    public synchronized List<byte[]> finish() {
        if (finished) {
            return List.of();
        }
        finished = true;

        List<byte[]> frames = drainCompleteFrames();
        byte[] remainder = pending.toByteArray();
        pending.reset();
        if (remainder.length > 0) {
            frames.add(remainder);
        }
        return List.copyOf(frames);
    }

    private List<byte[]> drainCompleteFrames() {
        byte[] available = pending.toByteArray();
        int completeFrameCount = available.length / frameBytes;
        if (completeFrameCount == 0) {
            return new ArrayList<>();
        }

        var frames = new ArrayList<byte[]>(completeFrameCount);
        int offset = 0;
        for (int index = 0; index < completeFrameCount; index++) {
            frames.add(java.util.Arrays.copyOfRange(available, offset, offset + frameBytes));
            offset += frameBytes;
        }

        pending.reset();
        pending.write(available, offset, available.length - offset);
        return frames;
    }

    private void ensureOpen() {
        if (finished) {
            throw new IllegalStateException("PCM packetizer is already finished");
        }
    }
}
