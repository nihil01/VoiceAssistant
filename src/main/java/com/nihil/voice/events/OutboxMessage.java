package com.nihil.voice.events;
import java.util.UUID;
public record OutboxMessage(UUID id,String eventType,String payload) {}
