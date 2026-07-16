package com.nihil.voice.conversation;

import java.util.ArrayList;
import java.util.List;

/** Builds short, speakable phrases from streaming LLM text deltas. */
final class StreamingTextChunker {
    private static final int DEFAULT_MAX_CHARACTERS = 180;

    private final int maxCharacters;
    private final StringBuilder pending = new StringBuilder();

    StreamingTextChunker() {
        this(DEFAULT_MAX_CHARACTERS);
    }

    StreamingTextChunker(int maxCharacters) {
        if (maxCharacters < 20) {
            throw new IllegalArgumentException("maxCharacters must be at least 20");
        }
        this.maxCharacters = maxCharacters;
    }

    List<String> append(String delta) {
        if (delta == null || delta.isEmpty()) {
            return List.of();
        }
        pending.append(delta);
        return drain(false);
    }

    List<String> finish() {
        return drain(true);
    }

    private List<String> drain(boolean flush) {
        var chunks = new ArrayList<String>();
        int boundary;
        while ((boundary = nextBoundary()) >= 0) {
            addChunk(chunks, boundary + 1);
        }

        if (flush && !pending.isEmpty()) {
            addChunk(chunks, pending.length());
        }
        return chunks;
    }

    private int nextBoundary() {
        for (int index = 0; index < pending.length(); index++) {
            char value = pending.charAt(index);
            if (value == '.' || value == '!' || value == '?' || value == '\n') {
                return index;
            }
        }

        if (pending.length() < maxCharacters) {
            return -1;
        }

        int whitespace = pending.lastIndexOf(" ", maxCharacters);
        return whitespace > 0 ? whitespace : maxCharacters - 1;
    }

    private void addChunk(List<String> chunks, int endExclusive) {
        String chunk = pending.substring(0, endExclusive).strip();
        pending.delete(0, endExclusive);
        while (!pending.isEmpty() && Character.isWhitespace(pending.charAt(0))) {
            pending.deleteCharAt(0);
        }
        if (!chunk.isBlank()) {
            chunks.add(chunk);
        }
    }
}
