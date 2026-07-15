package com.nihil.voice.llm;

import java.util.List;
import reactor.core.publisher.Flux;

@FunctionalInterface
public interface LlmClient { Flux<String> stream(List<ConversationMessage> messages); }
