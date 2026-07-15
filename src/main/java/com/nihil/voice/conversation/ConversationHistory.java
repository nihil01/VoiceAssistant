package com.nihil.voice.conversation;

import com.nihil.voice.llm.ConversationMessage;
import java.util.ArrayDeque;
import java.util.List;

public final class ConversationHistory {
    private final int maxMessages;
    private final ArrayDeque<ConversationMessage> messages = new ArrayDeque<>();
    public ConversationHistory(int maxMessages) {
        if (maxMessages < 2) throw new IllegalArgumentException("maxMessages must be >= 2");
        this.maxMessages = maxMessages;
    }
    public synchronized void add(ConversationMessage message) {
        messages.addLast(message);
        while (messages.size() > maxMessages) messages.removeFirst();
    }
    public synchronized List<ConversationMessage> snapshot() { return List.copyOf(messages); }
}
