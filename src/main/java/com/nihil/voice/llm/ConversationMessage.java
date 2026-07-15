package com.nihil.voice.llm;

import java.util.Objects;

public record ConversationMessage(Role role, String content) {
    public ConversationMessage { Objects.requireNonNull(role); Objects.requireNonNull(content); }
    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }
}
