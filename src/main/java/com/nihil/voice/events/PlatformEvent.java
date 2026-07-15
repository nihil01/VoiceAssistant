package com.nihil.voice.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PlatformEvent(UUID tenantId, UUID aggregateId, String type, Map<String, Object> payload, Instant occurredAt) {}
